/* Lightweight lightbox for .zoomable images. Keyboard: Esc close, ←/→ switch. */
(function () {
    const overlay = document.createElement('div');
    overlay.className = 'lightbox';
    overlay.setAttribute('role', 'dialog');
    overlay.setAttribute('aria-label', '图片浏览');
    overlay.innerHTML = '<button class="lightbox-close" aria-label="关闭">×</button>' +
        '<img class="lightbox-img" alt="">';
    document.addEventListener('DOMContentLoaded', () => document.body.appendChild(overlay));

    let images = [];
    let index = -1;
    let lastFocus = null;

    function open(list, i, opener) {
        images = list;
        index = i;
        lastFocus = opener;
        show();
        overlay.classList.add('open');
        overlay.querySelector('.lightbox-close').focus();
    }

    function show() {
        const img = overlay.querySelector('.lightbox-img');
        img.src = images[index].src;
        img.alt = images[index].alt || '';
    }

    function close() {
        overlay.classList.remove('open');
        if (lastFocus) lastFocus.focus();
    }

    document.addEventListener('click', (e) => {
        if (e.target.matches('img.zoomable')) {
            const all = Array.from(document.querySelectorAll('img.zoomable'));
            open(all, all.indexOf(e.target), e.target);
        } else if (e.target === overlay || e.target.matches('.lightbox-close')) {
            close();
        }
    });

    document.addEventListener('keydown', (e) => {
        if (!overlay.classList.contains('open')) return;
        if (e.key === 'Escape') close();
        if (e.key === 'ArrowRight' && images.length) { index = (index + 1) % images.length; show(); }
        if (e.key === 'ArrowLeft' && images.length) { index = (index - 1 + images.length) % images.length; show(); }
    });
})();
