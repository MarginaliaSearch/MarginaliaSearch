document.getElementById("language-button").addEventListener("click", function() {
    document.getElementById("language-menu").classList.toggle("hidden");
});
document.getElementById("language-button-sidebar").addEventListener("click", function() {
    document.getElementById("language-menu").classList.toggle("hidden");
});
document.querySelectorAll("#hide-language-button").forEach(button => {
    button.addEventListener("click", function() {
        document.getElementById("language-menu").classList.toggle("hidden");
    });
});
document.querySelectorAll(".hide-language-button").forEach(button => {
    button.addEventListener("click", function() {
        document.getElementById("language-menu").classList.toggle("hidden");
    });
});