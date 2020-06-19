var variantId = 11;
[db.config, db.config_anon].forEach(function(coll) {
  coll.update({ 'filter.v.1': { $exists: true } }, { $push: { 'filter.v': NumberInt(variantId) } }, { multi: true });
  coll.update({ 'filter.e': "800-2900"}, { $set: { 'filter.e': "600-2900" } }, { multi: true });
});