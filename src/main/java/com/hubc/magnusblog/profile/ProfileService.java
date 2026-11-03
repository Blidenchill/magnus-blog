package com.hubc.magnusblog.profile;

import com.hubc.magnusblog.common.InvalidInputException;
import com.hubc.magnusblog.media.MediaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Single-row site profile. Changes go live immediately (REQ-001, /admin/settings). */
@Service
public class ProfileService {

    private static final UUID PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final SiteProfileRepository repository;
    private final MediaService mediaService;

    public ProfileService(SiteProfileRepository repository, MediaService mediaService) {
        this.repository = repository;
        this.mediaService = mediaService;
    }

    @Transactional
    public SiteProfile getOrCreate() {
        return repository.findById(PROFILE_ID).orElseGet(() -> {
            SiteProfile profile = new SiteProfile();
            profile.setId(PROFILE_ID);
            profile.setName("");
            return repository.save(profile);
        });
    }

    @Transactional
    public void update(String name, String bio, String contact, UUID avatarMediaId, long version) {
        SiteProfile profile = getOrCreate();
        if (profile.getVersion() != version) {
            throw new com.hubc.magnusblog.common.ConflictException("资料已被其他窗口修改，请刷新后重试");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        if (name == null || name.isBlank()) {
            errors.put("name", "请填写名字");
        } else if (name.length() > 120) {
            errors.put("name", "名字最多 120 个字符");
        }
        if (contact != null && contact.length() > 500) {
            errors.put("contact", "联系方式最多 500 个字符");
        }
        if (!errors.isEmpty()) {
            throw new InvalidInputException(errors);
        }
        if (avatarMediaId != null) {
            mediaService.assertReady(java.util.Set.of(avatarMediaId));
        }
        profile.setName(name.trim());
        profile.setBio(bio);
        profile.setContact(contact);
        profile.setAvatarMediaId(avatarMediaId);
        profile.setUpdatedAt(Instant.now());
        repository.save(profile);
    }
}
