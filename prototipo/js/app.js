document.addEventListener('DOMContentLoaded', () => {
    // Seleciona todos os botões de navegação e todas as telas (views)
    const navItems = document.querySelectorAll('.nav-item');
    const views = document.querySelectorAll('.view');

    navItems.forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault(); // Evita que a página recarregue ao clicar no link #

            // 1. Remove a classe 'active' de todos os botões do menu
            navItems.forEach(nav => nav.classList.remove('active'));

            // 2. Remove a classe 'active' de todas as views (telas)
            views.forEach(view => view.classList.remove('active'));

            // 3. Adiciona a classe 'active' no botão clicado
            item.classList.add('active');

            // 4. Pega o ID da tela correspondente pelo 'data-target' e mostra ela
            const targetId = item.getAttribute('data-target');
            document.getElementById(targetId).classList.add('active');
        });
    });
});