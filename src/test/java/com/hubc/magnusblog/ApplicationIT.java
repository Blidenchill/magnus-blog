package com.hubc.magnusblog;

import com.hubc.magnusblog.content.ContentEntry;
import com.hubc.magnusblog.content.ContentEntryRepository;
import com.hubc.magnusblog.content.ContentRevisionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end slice through security, publishing and media rules against a real
 * PostgreSQL (Testcontainers). Skipped automatically when Docker is absent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.admin.username", () -> "admin");
        registry.add("magnus_admin_password-hash",
                () -> "$2a$10$ATnjoX1UTbvzMkbgwYoG9OtjHT/ZE2PpuiYSxojxUeYdRDaij9XHi"); // "test-password"
        registry.add("app.storage.root", () -> {
            try {
                return Files.createTempDirectory("magnus-it").toString();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        registry.add("server.servlet.session.cookie.secure", () -> "false");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ContentEntryRepository entryRepository;

    @Autowired
    ContentRevisionRepository revisionRepository;

    // ------------------------------------------------------------ REQ-001

    @Test
    void homeIsPublicAndShowsNoAdminControls() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/admin/contents/new"))));
    }

    // ------------------------------------------------------------ REQ-002 / REQ-003

    @Test
    void anonymousIsRedirectedFromAdmin() throws Exception {
        mvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/admin/login")));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(formLogin("/admin/login").user("admin").password("wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?error"));
    }

    @Test
    void correctCredentialsLogIn() throws Exception {
        mvc.perform(formLogin("/admin/login").user("admin").password("test-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    void postWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/admin/contents/new").with(user("admin").roles("ADMIN"))
                        .param("type", "BLOG"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotCreateContent() throws Exception {
        mvc.perform(post("/admin/contents/new").with(csrf()).param("type", "BLOG"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/admin/login")));
    }

    // ------------------------------------------------------------ REQ-005 / REQ-008 lifecycle

    @Test
    void blogLifecycleDraftPublishUnpublish() throws Exception {
        // create
        MvcResult created = mvc.perform(post("/admin/contents/new")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("type", "BLOG"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = created.getResponse().getRedirectedUrl();
        UUID entryId = UUID.fromString(location.replaceFirst(".*/admin/contents/", "").replace("/edit", ""));

        ContentEntry entry = entryRepository.findById(entryId).orElseThrow();
        String slug = entry.getSlug();

        // draft is invisible to anonymous visitors
        mvc.perform(get("/blog/" + slug)).andExpect(status().isNotFound());

        // save draft with an XSS attempt embedded
        mvc.perform(post("/admin/contents/" + entryId + "/edit")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("entryVersion", String.valueOf(entry.getVersion()))
                        .param("revisionVersion", "0")
                        .param("title", "测试文章")
                        .param("bodyHtml", "<p>正文</p><script>alert(1)</script>")
                        .param("detailJson", "{}"))
                .andExpect(status().is3xxRedirection());

        // publish
        entry = entryRepository.findById(entryId).orElseThrow();
        long revVersion = revisionRepository.findById(entry.getDraftRevisionId()).orElseThrow().getVersion();
        mvc.perform(post("/admin/contents/" + entryId + "/publish")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("entryVersion", String.valueOf(entry.getVersion()))
                        .param("revisionVersion", String.valueOf(revVersion)))
                .andExpect(status().is3xxRedirection());

        // now public, and the script was sanitized on save
        mvc.perform(get("/blog/" + slug))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("测试文章")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<script>"))));

        // unpublish -> anonymous 404 again
        entry = entryRepository.findById(entryId).orElseThrow();
        mvc.perform(post("/admin/contents/" + entryId + "/unpublish")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("entryVersion", String.valueOf(entry.getVersion())))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/blog/" + slug)).andExpect(status().isNotFound());
    }

    @Test
    void optimisticLockConflictReturns409() throws Exception {
        MvcResult created = mvc.perform(post("/admin/contents/new")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("type", "BLOG"))
                .andReturn();
        UUID entryId = UUID.fromString(created.getResponse().getRedirectedUrl()
                .replaceFirst(".*/admin/contents/", "").replace("/edit", ""));
        mvc.perform(post("/admin/contents/" + entryId + "/edit")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("entryVersion", "999")   // stale version
                        .param("revisionVersion", "0")
                        .param("title", "x")
                        .param("detailJson", "{}"))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------ REQ-010 media rules

    @Test
    void uploadRequiresAdmin() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", pngBytes());
        mvc.perform(multipart("/admin/media/upload").file(file).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void uploadWorksAndUnreferencedMediaIsNotPublic() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", pngBytes());
        MvcResult result = mvc.perform(multipart("/admin/media/upload").file(file)
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        // not referenced by published content -> anonymous 404
        mvc.perform(get("/media/" + id + "/display")).andExpect(status().isNotFound());
        // admin can view
        mvc.perform(get("/admin/media/" + id + "/display").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        // original is never public
        mvc.perform(get("/media/" + id + "/original")).andExpect(status().isNotFound());
    }

    @Test
    void uploadRejectsNonImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png",
                "definitely not an image".getBytes());
        mvc.perform(multipart("/admin/media/upload").file(file)
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    private static byte[] pngBytes() throws Exception {
        BufferedImage img = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0x00FF00);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
