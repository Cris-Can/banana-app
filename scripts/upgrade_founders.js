/**
 * 🚀 Script: Upgrade All Existing Users to FOUNDER/GOLD Status
 * 
 * Usa subscriptionType = "GOLD" para compatibilidad con la versión vieja del Play Store.
 * La nueva versión de la app auto-repara a "FOUNDER" cuando el usuario actualice.
 * 
 * USO:
 * 1. Descargá tu Service Account Key desde Firebase Console:
 *    → Project Settings → Service Accounts → Generate New Private Key
 *    → Guardalo como "serviceAccountKey.json" en esta misma carpeta (scripts/)
 * 
 * 2. Instalá firebase-admin:
 *    npm install firebase-admin
 * 
 * 3. Ejecutá:
 *    node upgrade_founders.js
 */

const admin = require('firebase-admin');

// 🔑 Cargar credenciales
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function upgradeAllToFounder() {
    console.log('🚀 Iniciando upgrade de usuarios a FOUNDER...\n');

    // 1. Leer todos los usuarios
    const usersSnapshot = await db.collection('users').get();
    const totalUsers = usersSnapshot.size;

    console.log(`📊 Total de usuarios encontrados: ${totalUsers}\n`);

    let updated = 0;
    let alreadyFounder = 0;
    let errors = 0;

    // 2. Batch update (Firestore max 500 por batch)
    const batches = [];
    let currentBatch = db.batch();
    let batchCount = 0;

    for (const doc of usersSnapshot.docs) {
        const data = doc.data();
        const uid = doc.id;
        const nickname = data.nickname || 'Sin nombre';
        const currentType = data.subscriptionType || 'FREE';
        const isFounder = data.isFounder === true;

        if (isFounder && (currentType === 'FOUNDER' || currentType === 'GOLD') && data.isGold === true) {
            console.log(`  ✅ ${nickname} (${uid}) — Ya es ${currentType} con isFounder, skip`);
            alreadyFounder++;
            continue;
        }

        console.log(`  🔄 ${nickname} (${uid}) — ${currentType} → GOLD (isFounder=true)`);

        currentBatch.update(doc.ref, {
            isFounder: true,
            isGold: true,           // isGoldStored field
            premium: true,          // isPremiumStored legacy field
            subscriptionType: 'GOLD'  // GOLD para compatibilidad con app vieja, auto-repair a FOUNDER en app nueva
        });

        updated++;
        batchCount++;

        // Firestore limit: 500 operations per batch
        if (batchCount >= 500) {
            batches.push(currentBatch);
            currentBatch = db.batch();
            batchCount = 0;
        }
    }

    // Push last batch
    if (batchCount > 0) {
        batches.push(currentBatch);
    }

    // 3. Execute all batches
    console.log(`\n⏳ Ejecutando ${batches.length} batch(es)...`);

    for (let i = 0; i < batches.length; i++) {
        try {
            await batches[i].commit();
            console.log(`  ✅ Batch ${i + 1}/${batches.length} completado`);
        } catch (e) {
            console.error(`  ❌ Batch ${i + 1} falló:`, e.message);
            errors++;
        }
    }

    // 4. Update config/stats to reflect correct userCount
    try {
        await db.collection('config').doc('stats').set(
            { userCount: totalUsers },
            { merge: true }
        );
        console.log(`\n📊 config/stats/userCount actualizado a ${totalUsers}`);
    } catch (e) {
        console.error('⚠️ No se pudo actualizar config/stats:', e.message);
    }

    // 5. Summary
    console.log('\n' + '='.repeat(50));
    console.log('📋 RESUMEN:');
    console.log(`   Total usuarios:     ${totalUsers}`);
    console.log(`   Actualizados:       ${updated}`);
    console.log(`   Ya eran FOUNDER:    ${alreadyFounder}`);
    console.log(`   Errores:            ${errors}`);
    console.log('='.repeat(50));
    console.log('\n✅ ¡Listo! Todos los usuarios existentes son FOUNDER 🚀');

    process.exit(0);
}

upgradeAllToFounder().catch((err) => {
    console.error('❌ Error fatal:', err);
    process.exit(1);
});
