print("Enable chat for all teams");

db.team.find().forEach(t => {
  db.team.update({_id:t._id},{$set:{chat:true}});
});

print("Done!");