const admin = require("firebase-admin");
const path = require("path");

let serviceAccount;
try {
  serviceAccount = require(path.join(__dirname, "..", "functions", "service-account.json"));
} catch (e) {
  console.log("No service-account.json found in functions. Using default/ADC initialization.");
}

if (!admin.apps.length) {
  if (serviceAccount) {
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
    });
  } else {
    admin.initializeApp({
      projectId: "bananaapp-aa46e",
    });
  }
}

const db = admin.firestore();

async function migrate() {
  console.log("Starting migration...");
  const snapshot = await db.collection("events").get();
  console.log(`Found ${snapshot.size} events in total.`);
  
  let migrated = 0;
  let skipped = 0;

  const batch = db.batch();
  let opCount = 0;

  for (const doc of snapshot.docs) {
    const data = doc.data();
    const createdAt = data.createdAt;

    // Check if it's a Timestamp (has toDate method)
    if (createdAt && typeof createdAt === "object" && typeof createdAt.toDate === "function") {
      batch.update(doc.ref, { createdAt: createdAt.toDate().getTime() });
      migrated++;
      opCount++;

      // Firestore batch limit is 500 operations
      if (opCount >= 500) {
        await batch.commit();
        console.log(`Batch committed (${migrated} so far)`);
        opCount = 0;
      }
    } else {
      skipped++;
    }
  }

  if (opCount > 0) {
    await batch.commit();
  }

  console.log(`Done. Migrated: ${migrated}, Skipped (already Long): ${skipped}`);
}

migrate().catch(console.error);
