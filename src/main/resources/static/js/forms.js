/* Admin forms: data-confirm="message" shows a confirmation before submit. */
document.addEventListener('submit', (e) => {
    const form = e.target;
    if (form instanceof HTMLFormElement && form.dataset.confirm) {
        if (!window.confirm(form.dataset.confirm)) {
            e.preventDefault();
        }
    }
});
