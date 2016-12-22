$(function() {
    setTimeout(function() {
        layout.sizePane('east', '100%');
        $('.ui-layout-center').remove();
        $('.ui-draggable-handle').remove();
    }, 1000);
});
