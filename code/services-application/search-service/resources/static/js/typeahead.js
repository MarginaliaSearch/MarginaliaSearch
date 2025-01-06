const searchInput = document.getElementById('searchInput');
const suggestionsContainer = document.getElementById('searchSuggestions');
let currentSuggestions = [];
let selectedIndex = -1;

// Debounce function to limit API calls
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

// Fetch suggestions from the API
async function fetchSuggestions(partial) {
    if (!partial) {
        suggestionsContainer.classList.add('hidden');
        return;
    }

    try {
        const response = await fetch(`/suggest/?partial=${encodeURIComponent(partial)}`);
        if (!response.ok) throw new Error('Network response was not ok');

        const suggestions = await response.json();
        currentSuggestions = suggestions;
        displaySuggestions(suggestions);
    } catch (error) {
        console.error('Error fetching suggestions:', error);
    }
}

// Display suggestions in the dropdown
function displaySuggestions(suggestions) {
    if (!suggestions.length) {
        suggestionsContainer.classList.add('hidden');
        return;
    }

    suggestionsContainer.innerHTML = suggestions.map((suggestion, index) => `
                <div 
                    class="suggestion px-4 py-2 cursor-pointer hover:bg-gray-100 ${index === selectedIndex ? 'bg-blue-50' : ''}"
                    data-index="${index}"
                >
                    ${suggestion}
                </div>
            `).join('');

    suggestionsContainer.classList.remove('hidden');

    // Add click handlers to suggestions
    document.querySelectorAll('.suggestion').forEach(el => {
        el.addEventListener('click', () => {
            searchInput.value = el.textContent.trim();
            suggestionsContainer.classList.add('hidden');
        });
    });
}

// Handle keyboard navigation
searchInput.addEventListener('keydown', (e) => {
    if (!currentSuggestions.length) return;

    switch(e.key) {
        case 'ArrowDown':
            e.preventDefault();
            selectedIndex = Math.min(selectedIndex + 1, currentSuggestions.length - 1);
            displaySuggestions(currentSuggestions);
            break;
        case 'ArrowUp':
            e.preventDefault();
            selectedIndex = Math.max(selectedIndex - 1, -1);
            displaySuggestions(currentSuggestions);
            break;
        case 'Enter':
            if (selectedIndex >= 0) {
                searchInput.value = currentSuggestions[selectedIndex];
                suggestionsContainer.classList.add('hidden');
                selectedIndex = -1;
            }
            break;
        case 'Escape':
            suggestionsContainer.classList.add('hidden');
            selectedIndex = -1;
            break;
    }
});

// Handle input changes with debouncing
searchInput.addEventListener('input', debounce((e) => {
    selectedIndex = -1;
    fetchSuggestions(e.target.value.trim());
}, 150));

// Close suggestions when clicking outside
document.addEventListener('click', (e) => {
    if (!suggestionsContainer.contains(e.target) && e.target !== searchInput) {
        suggestionsContainer.classList.add('hidden');
    }
});