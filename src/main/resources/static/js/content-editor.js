/* Content editor: dynamic detail rows, image uploads, Quill body, detailJson build, autosave.
   Config comes from the #editor-data element (CSP-safe: no inline scripts). */
(function () {
    const dataEl = document.getElementById('editor-data');
    const form = document.getElementById('content-form');
    if (!dataEl || !form) return;

    const type = dataEl.dataset.type;
    let initial = {};
    try {
        initial = JSON.parse(dataEl.dataset.detail || '{}');
    } catch (e) {
        initial = {};
    }
    const autosaveUrl = dataEl.dataset.autosaveUrl;

    const csrfToken = document.querySelector('meta[name="_csrf"]');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]');

    // ---------------------------------------------------------------- uploads
    async function upload(file) {
        const data = new FormData();
        data.append('file', file);
        const headers = {};
        if (csrfToken && csrfHeader) headers[csrfHeader.content] = csrfToken.content;
        const resp = await fetch('/admin/media/upload', {method: 'POST', body: data, headers});
        const json = await resp.json().catch(() => ({}));
        if (!resp.ok) throw new Error(json.error || ('上传失败（' + resp.status + '）'));
        return json;
    }

    function pickFile(onFile) {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/jpeg,image/png';
        input.addEventListener('change', () => {
            if (input.files.length) {
                onUpload(input.files[0], input).catch(err => alert(err.message));
            }
        });
        input.click();
        function onUpload(f) {
            return upload(f).then(result => {
                if (input._onDone) input._onDone(result);
            });
        }
        return input;
    }

    // ---------------------------------------------------------------- cover
    const coverBtn = document.querySelector('[data-upload-for="cover"]');
    if (coverBtn) {
        coverBtn.addEventListener('click', () => {
            const input = pickFile();
            input._onDone = (result) => {
                document.getElementById('coverMediaId').value = result.id;
                let img = document.getElementById('coverPreview');
                if (!img) {
                    img = document.createElement('img');
                    img.id = 'coverPreview';
                    coverBtn.parentElement.prepend(img);
                }
                img.src = result.previewUrl;
            };
        });
    }
    const clearCover = document.querySelector('[data-clear-cover]');
    if (clearCover) {
        clearCover.addEventListener('click', () => {
            document.getElementById('coverMediaId').value = '';
            const img = document.getElementById('coverPreview');
            if (img) img.remove();
        });
    }

    // ---------------------------------------------------------------- row helpers
    function el(tag, className, text) {
        const e = document.createElement(tag);
        if (className) e.className = className;
        if (text !== undefined) e.textContent = text;
        return e;
    }

    function field(className, placeholder, value) {
        const input = el('input', className);
        input.type = 'text';
        input.placeholder = placeholder || '';
        input.value = value || '';
        return input;
    }

    function rowControls(list, li) {
        const controls = el('span', 'row-controls');
        const up = el('button', 'btn btn-quiet', '↑');
        up.type = 'button';
        up.title = '上移';
        up.addEventListener('click', () => {
            if (li.previousElementSibling) list.insertBefore(li, li.previousElementSibling);
        });
        const down = el('button', 'btn btn-quiet', '↓');
        down.type = 'button';
        down.title = '下移';
        down.addEventListener('click', () => {
            if (li.nextElementSibling) list.insertBefore(li.nextElementSibling, li);
        });
        const del = el('button', 'btn btn-quiet', '删除');
        del.type = 'button';
        del.addEventListener('click', () => li.remove());
        controls.append(up, down, del);
        return controls;
    }

    function imageSlot(li, mediaId, displayUrl) {
        const slot = el('span', 'img-slot');
        if (mediaId) {
            slot.dataset.mediaId = mediaId;
            const img = document.createElement('img');
            img.src = displayUrl || ('/admin/media/' + mediaId + '/thumb');
            img.alt = '';
            slot.appendChild(img);
        }
        const btn = el('button', 'btn btn-quiet', mediaId ? '换图' : '传图');
        btn.type = 'button';
        btn.addEventListener('click', () => {
            const input = pickFile();
            input._onDone = (result) => {
                slot.dataset.mediaId = result.id;
                slot.querySelectorAll('img').forEach(i => i.remove());
                const img = document.createElement('img');
                img.src = result.previewUrl;
                img.alt = '';
                slot.prepend(img);
                btn.textContent = '换图';
            };
        });
        slot.appendChild(btn);
        return slot;
    }

    // ---------------------------------------------------------------- PHOTO
    const photoList = document.getElementById('photo-items');
    function addPhotoItem(item) {
        const li = el('li', 'edit-row');
        li.appendChild(imageSlot(li, item.mediaId, item.displayUrl));
        li.appendChild(field('alt', '替代文本（无障碍）', item.altText));
        li.appendChild(field('caption', '说明文字', item.caption));
        li.appendChild(rowControls(photoList, li));
        photoList.appendChild(li);
    }
    if (photoList) {
        (initial.photos || []).forEach(addPhotoItem);
        const addBtn = document.querySelector('[data-upload-for="photo"]');
        addBtn.addEventListener('click', () => {
            const input = pickFile();
            input._onDone = (result) => addPhotoItem({mediaId: result.id, displayUrl: result.previewUrl});
        });
    }

    // ---------------------------------------------------------------- FOOD
    const ingredientList = document.getElementById('ingredient-items');
    function addIngredient(item) {
        const li = el('li', 'edit-row');
        li.appendChild(field('name', '食材名称', item.name));
        li.appendChild(field('amount', '用量', item.amount));
        li.appendChild(rowControls(ingredientList, li));
        ingredientList.appendChild(li);
    }
    const stepList = document.getElementById('step-items');
    function addStep(item) {
        const li = el('li', 'edit-row');
        li.appendChild(field('text', '步骤描述', item.text));
        li.appendChild(imageSlot(li, item.mediaId, item.displayUrl));
        li.appendChild(field('alt', '图片替代文本', item.altText));
        li.appendChild(rowControls(stepList, li));
        stepList.appendChild(li);
    }
    if (ingredientList) {
        (initial.ingredients || []).forEach(addIngredient);
        document.querySelector('[data-add-row="ingredient"]').addEventListener('click', () => addIngredient({}));
        (initial.steps || []).forEach(addStep);
        document.querySelector('[data-add-row="step"]').addEventListener('click', () => addStep({}));
        if (initial.recipe) {
            setDetailField('difficulty', initial.recipe.difficulty);
            setDetailField('durationMinutes', initial.recipe.durationMinutes);
            setDetailField('servings', initial.recipe.servings);
            setDetailField('intro', initial.recipe.intro);
        }
    }
    function setDetailField(name, value) {
        const e = document.querySelector('[data-detail-field="' + name + '"]');
        if (e && value !== null && value !== undefined) e.value = value;
    }
    function getDetailField(name) {
        const e = document.querySelector('[data-detail-field="' + name + '"]');
        return e ? e.value : null;
    }

    // ---------------------------------------------------------------- TRAVEL
    const destList = document.getElementById('destination-items');
    function addDestination(item) {
        const li = el('li', 'edit-row edit-row-tall');
        li.appendChild(field('name', '景点名称', item.name));
        li.appendChild(field('region', '地区', item.region));
        li.appendChild(field('summary', '简介', item.summary));
        li.appendChild(field('reason', '推荐理由', item.recommendReason));
        const date = el('input', 'visit-date');
        date.type = 'date';
        date.value = item.visitDate || '';
        li.appendChild(date);
        const map = field('map-url', '地图链接（https://…，可选）', item.mapUrl);
        map.type = 'url';
        map.inputMode = 'url';
        li.appendChild(map);
        li.appendChild(imageSlot(li, item.mediaId, item.displayUrl));
        li.appendChild(field('alt', '图片替代文本', item.altText));
        li.appendChild(rowControls(destList, li));
        destList.appendChild(li);
    }
    if (destList) {
        (initial.destinations || []).forEach(addDestination);
        document.querySelector('[data-add-row="destination"]').addEventListener('click', () => addDestination({}));
    }

    // ---------------------------------------------------------------- Quill (BLOG)
    let quill = null;
    const quillDiv = document.getElementById('quill-editor');
    if (quillDiv && window.Quill) {
        quill = new Quill(quillDiv, {
            theme: 'snow',
            placeholder: '正文内容…',
            modules: {
                toolbar: {
                    container: [
                        [{header: [2, 3, 4, false]}],
                        ['bold', 'italic', 'underline', 'strike'],
                        [{list: 'ordered'}, {list: 'bullet'}],
                        ['blockquote', 'link', 'image']
                    ],
                    handlers: {
                        image: function () {
                            const input = pickFile();
                            input._onDone = (result) => {
                                const range = quill.getSelection(true);
                                // Store the canonical public URL; preview swaps happen server-side.
                                quill.insertEmbed(range.index, 'image', result.publishedUrl);
                            };
                        }
                    }
                }
            }
        });
        // Editor shows admin-protected URLs; the stored body keeps public URLs.
        const initialHtml = (document.getElementById('bodyHtml').value || '')
            .replaceAll('src="/media/', 'src="/admin/media/');
        quill.clipboard.dangerouslyPasteHTML(initialHtml);
    }

    // ---------------------------------------------------------------- build detailJson
    function buildDetailJson() {
        if (type === 'PHOTO') {
            const items = [];
            photoList.querySelectorAll('li').forEach(li => {
                const slot = li.querySelector('.img-slot');
                if (slot && slot.dataset.mediaId) {
                    items.push({
                        mediaId: slot.dataset.mediaId,
                        altText: li.querySelector('.alt').value,
                        caption: li.querySelector('.caption').value
                    });
                }
            });
            return {items};
        }
        if (type === 'FOOD') {
            const ingredients = [];
            ingredientList.querySelectorAll('li').forEach(li => {
                const name = li.querySelector('.name').value.trim();
                if (name) ingredients.push({name, amount: li.querySelector('.amount').value.trim()});
            });
            const steps = [];
            stepList.querySelectorAll('li').forEach(li => {
                const slot = li.querySelector('.img-slot');
                steps.push({
                    text: li.querySelector('.text').value.trim(),
                    mediaId: slot ? slot.dataset.mediaId || null : null,
                    altText: li.querySelector('.alt').value.trim()
                });
            });
            const duration = parseInt(getDetailField('durationMinutes'), 10);
            return {
                difficulty: getDetailField('difficulty'),
                durationMinutes: Number.isNaN(duration) ? null : duration,
                servings: getDetailField('servings'),
                intro: getDetailField('intro'),
                ingredients, steps
            };
        }
        if (type === 'TRAVEL') {
            const destinations = [];
            destList.querySelectorAll('li').forEach(li => {
                const name = li.querySelector('.name').value.trim();
                if (!name) return;
                const slot = li.querySelector('.img-slot');
                destinations.push({
                    name,
                    region: li.querySelector('.region').value.trim(),
                    summary: li.querySelector('.summary').value.trim(),
                    recommendReason: li.querySelector('.reason').value.trim(),
                    visitDate: li.querySelector('.visit-date').value || null,
                    mapUrl: li.querySelector('.map-url').value.trim() || null,
                    mediaId: slot ? slot.dataset.mediaId || null : null,
                    altText: li.querySelector('.alt').value.trim()
                });
            });
            return {destinations};
        }
        return {};
    }

    function collectBodyHtml() {
        if (!quill) return document.getElementById('bodyHtml') ? document.getElementById('bodyHtml').value : '';
        return quill.root.innerHTML.replaceAll('src="/admin/media/', 'src="/media/');
    }

    form.addEventListener('submit', () => {
        document.getElementById('detailJson').value = JSON.stringify(buildDetailJson());
        const bodyInput = document.getElementById('bodyHtml');
        if (bodyInput) bodyInput.value = collectBodyHtml();
    });

    // ---------------------------------------------------------------- autosave
    const status = document.getElementById('autosave-status');
    let dirty = false;
    form.addEventListener('input', () => { dirty = true; });

    setInterval(async () => {
        if (!dirty) return;
        document.getElementById('detailJson').value = JSON.stringify(buildDetailJson());
        const bodyInput = document.getElementById('bodyHtml');
        if (bodyInput) bodyInput.value = collectBodyHtml();
        const data = new FormData(form);
        try {
            const resp = await fetch(autosaveUrl || form.action.replace(/\/edit$/, '/autosave'), {
                method: 'POST', body: data
            });
            const json = await resp.json().catch(() => ({}));
            if (resp.ok) {
                dirty = false;
                form.querySelector('[name="entryVersion"]').value = json.entryVersion;
                form.querySelector('[name="revisionVersion"]').value = json.revisionVersion;
                status.textContent = '已自动保存 ' + new Date().toLocaleTimeString();
            } else if (resp.status === 409) {
                status.textContent = '自动保存失败：内容在其他窗口被修改，请刷新页面';
            } else {
                status.textContent = '自动保存失败：' + (json.error || resp.status);
            }
        } catch (e) {
            status.textContent = '自动保存失败：网络错误';
        }
    }, 30000);
})();
