document.getElementById("filter-button").addEventListener("click", function() {
    document.getElementById("mobile-menu").classList.toggle("hidden");
});

document.querySelectorAll("#hide-filter-button").forEach(button => {
    button.addEventListener("click", function() {
        document.getElementById("mobile-menu").classList.toggle("hidden");
    });
});
document.querySelectorAll(".hide-filter-button").forEach(button => {
    button.addEventListener("click", function() {
        document.getElementById("mobile-menu").classList.toggle("hidden");
    });
});