<%--  Header components for live reloading in test environments --%>
<script src="https://cdn.tailwindcss.com"></script>
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css">
<script>
    tailwind.config = {
        theme: {
            extend: {
                colors: {
                    nicotine: '#f8f8ee',
                    margeblue: '#3e5f6f',
                    liteblue: '#0066cc',
                },
                screens: {
                    'coarsepointer': {
                        'raw': '(pointer: coarse)'
                    },
                    'finepointer': {
                        'raw': '(pointer: fine)'
                    },
                }
            },
            screens: {
                'xs': '440px',
                'sm': '640px',
                'md': '768px',
                'lg': '1024px',
                'xl': '1280px',
                '2xl': '1536px',
            },
        }
    }
</script>