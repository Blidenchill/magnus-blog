/* Profile settings page: avatar upload with live preview. */
document.addEventListener('DOMContentLoaded', () => {
    const input = document.getElementById('avatar-upload-input');
    const btn = document.getElementById('avatar-upload-btn');
    if (!input || !btn) return;
    btn.addEventListener('click', () => input.click());
    input.addEventListener('change', async () => {
        if (!input.files.length) return;
        try {
            const result = await window.magnusUpload.uploadImage(input.files[0]);
            document.getElementById('avatarMediaId').value = result.id;
            let img = document.getElementById('avatarPreview');
            if (!img) {
                img = document.createElement('img');
                img.id = 'avatarPreview';
                img.alt = '头像预览';
                document.querySelector('.cover-slot').prepend(img);
            }
            img.src = result.previewUrl;
        } catch (err) {
            window.alert('上传失败：' + err.message);
        }
    });
});
