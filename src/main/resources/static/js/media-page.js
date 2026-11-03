/* Media library page: batch upload via shared uploader, reload on completion. */
document.addEventListener('DOMContentLoaded', () => {
    const input = document.getElementById('media-upload-input');
    if (!input) return;
    input.addEventListener('change', async (e) => {
        const status = document.getElementById('upload-status');
        for (const file of e.target.files) {
            status.textContent = '上传中：' + file.name;
            try {
                await window.magnusUpload.uploadImage(file);
            } catch (err) {
                status.textContent = '上传失败：' + err.message;
                return;
            }
        }
        location.reload();
    });
});
