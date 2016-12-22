$(function() {
    setTimeout(function() {
        // a little gap to top
        window.addEventListener("hashchange", function() {
            $('.ui-layout-east').scrollTop($('.ui-layout-east').scrollTop() - 6);
        });
        // scroll to hash element
        if(window.location.hash.length > 0) {
            $('.ui-layout-east').scrollTop($(window.location.hash).offset().top - 30);
        }
    }, 1000);
});
