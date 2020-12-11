$(function() {

  var $variant = $('#form3-variant');
  var $positionStandard = $('.form3 .position-standard');
  var $positionRussian = $('.form3 .position-russian');
  var $positionBrazilian = $('.form3 .position-brazilian');
  function showPosition() {
    $positionStandard.toggleNone($variant.val() == 1);
    $positionRussian.toggleNone($variant.val() == 11);
    $positionBrazilian.toggleNone($variant.val() == 12);
  };
  $variant.on('change', showPosition);
  showPosition();

  $('form .conditions a.show').on('click', function() {
    $(this).remove();
    $('form .conditions').addClass('visible');
  });

  if (!$("main div.crud").length) {
    $("main form .flatpickr").flatpickr({
      minDate: 'today',
      maxDate: new Date(Date.now() + 1000 * 3600 * 24 * 31),
      dateFormat: 'Z',
      altInput: true,
      altFormat: 'Y-m-d h:i K'
    });
  }
});
