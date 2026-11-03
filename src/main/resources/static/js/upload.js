/* Shared image upload helper for admin pages. Sends CSRF token from meta tags. */
window.magnusUpload = (function () {
    function csrf() {
        const token = document.querySelector('meta[name="_csrf"]');
        const header = document.querySelector('meta[name="_csrf_header"]');
        return token && header ? {name: header.content, value: token.content} : null;
    }

    async function uploadImage(file) {
        if (!file) throw new Error('未选择文件');
        if (file.size > 10 * 1024 * 1024) throw new Error('图片超过 10 MB 限制');
        const form = new FormData();
        form.append('file', file);
        const headers = {};
        const c = csrf();
        if (c) headers[c.name] = c.value;
        const resp = await fetch('/admin/media/upload', {method: 'POST', body: form, headers});
        const data = await resp.json().catch(() => ({}));
        if (!resp.ok) throw new Error(data.error || ('上传失败（' + resp.status + '）'));
        return data; // {id, previewUrl, publishedUrl, width, height}
    }

    return {uploadImage};
})();
