function getTheme() {
    const theme = window.localStorage.getItem('theme');

    // if a valid theme is set in localStorage, return it
    if (theme === 'dark' || theme === 'light') {
        return { value: theme, system: false };
    }

    // if matchMedia is supported and OS theme is dark
    if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
        return { value: 'dark', system: true };
    }

    return { value: 'light', system: true };
}

function setTheme(value) {
    if (value === 'dark' || value === 'light') {
        window.localStorage.setItem('theme', value);
    } else {
        window.localStorage.removeItem('theme');
    }

    const theme = getTheme();

    document.documentElement.setAttribute('data-theme', theme.value);
}

function initializeTheme() {
    const themeSelect = document.getElementById('theme-select');

    const theme = getTheme();

    document.documentElement.setAttribute('data-theme', theme.value);

    // system is selected by default in the themeSwitcher so ignore it here
    if (!theme.system) {
        themeSelect.value = theme.value;
    }

    themeSelect.addEventListener('change', e => {
        setTheme(e.target.value);
    });
    
    const mql = window.matchMedia('(prefers-color-scheme: dark)');

    // if someone changes their theme at the OS level we need to update 
    // their theme immediately if they're using their OS theme
    mql.addEventListener('change', e => {    
        if (themeSelect.value !== 'system') return;
        
        if (e.matches) setTheme('dark');
        else setTheme('light');
    });
}

initializeTheme();