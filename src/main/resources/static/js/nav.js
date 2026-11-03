/* Mobile nav toggle (no inline handlers; CSP-safe). */
document.addEventListener('DOMContentLoaded', () => {
    const toggle = document.getElementById('nav-toggle');
    if (!toggle) return;
    toggle.addEventListener('click', () => {
        document.body.classList.toggle('nav-open');
        toggle.setAttribute('aria-expanded', String(document.body.classList.contains('nav-open')));
    });
});
