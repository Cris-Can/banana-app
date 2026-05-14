/**
 * 🛠️ SANEAMIENTO MASIVO DISTRIBUIDO - SISTEMA SOCIAL (NIVEL PRODUCCIÓN)
 * 
 * USO:
 * 1. Mueve tu 'serviceAccountKey.json' a esta carpeta.
 * 2. npm install firebase-admin
 * 3. Ejecuta: node fix_friends.js
 */

const admin = require("firebase-admin");
const path = require("path");

// Carga local segura de credenciales
const serviceAccount = require(path.join(__dirname, "serviceAccountKey.json"));

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

// ----------------------------------------------------------------------
// ⚙️ CONFIGURACIÓN CRÍTICA
// ----------------------------------------------------------------------
const DRY_RUN = false;
// "conservative": Elimina vínculos no correspondidos (Amigo falso).
// "aggressive"  : Consolida vínculos (Fuerza la amistad bidireccional).
const RELATION_MODE = "conservative"; 

async function sanitizeDatabase() {
    console.log(`\n======================================================`);
    console.log(`🚀 INICIANDO AUDITORÍA SANEAMIENTO DE AMISTADES`);
    console.log(`🛡️ PARAM: DRY_RUN = ${DRY_RUN}`);
    console.log(`🛡️ PARAM: MODE    = ${RELATION_MODE.toUpperCase()}`);
    console.log(`======================================================\n`);
    
    const usersSnapshot = await db.collection("users").get();
    console.log(`📊 Total de Usuarios (Snapshot en RAM): ${usersSnapshot.size}\n`);

    // ----------------------------------------------------------------------
    // 1. CARGA EN MEMORIA Y DEDUPLICACIÓN O(N)
    // ----------------------------------------------------------------------
    const userMap = new Map();
    
    for (const doc of usersSnapshot.docs) {
        const data = doc.data();
        userMap.set(doc.id, {
            id: doc.id,
            ref: doc.ref,
            updateTime: doc.updateTime, // 🔒 Candado de concurrencia
            
            // Sets dinámicos para operaciones rápidas O(1)
            friends: new Set(data.friends || []),
            sent: new Set(data.friendRequestsSent || []),
            received: new Set(data.friendRequestsReceived || []),
            blocked: new Set(data.blockedUsers || []),
            
            // Estado original crudo para la validación final O(1)
            orig: {
                friendsCount: (data.friends || []).length,
                sentCount: (data.friendRequestsSent || []).length,
                receivedCount: (data.friendRequestsReceived || []).length
            },
            needsUpdate: false
        });
    }

    const metrics = {
        repairedFriendships: 0,
        removedGhostFriendships: 0,
        removedGhostRequests: 0,
        crossRequestsResolved: 0,
        blockedViolationsCleaned: 0,
        removedDuplicates: 0,
        skippedConsistent: 0,
        concurrencyRejections: 0 // Documentos no actualizados para evitar overwritten
    };

    // ----------------------------------------------------------------------
    // 2. MOTOR DE ANÁLISIS E INFERENCIA (Linear Scan O(N))
    // ----------------------------------------------------------------------
    for (const [uid, user] of userMap.entries()) {
        
        // --- 2.1 BLOQUEOS (Prioridad Máxima) ---
        const allRelations = new Set([...user.friends, ...user.sent, ...user.received]);
        
        for (const relatedUid of allRelations) {
            const relatedUser = userMap.get(relatedUid);
            
            if (!relatedUser) {
                console.log(`[REMOVE] removed dead UID ${relatedUid} from ${uid}`);
                user.friends.delete(relatedUid);
                user.sent.delete(relatedUid);
                user.received.delete(relatedUid);
                metrics.removedGhostFriendships++;
                continue;
            }

            const meBlockedThem = user.blocked.has(relatedUid);
            const themBlockedMe = relatedUser.blocked.has(uid);

            if (meBlockedThem || themBlockedMe) {
                if (user.friends.has(relatedUid) || user.sent.has(relatedUid) || user.received.has(relatedUid)) {
                    console.log(`[BLOCK] prevented relationship due to block constraint: ${uid} - ${relatedUid}`);
                    user.friends.delete(relatedUid);
                    user.sent.delete(relatedUid);
                    user.received.delete(relatedUid);
                    relatedUser.friends.delete(uid);
                    relatedUser.sent.delete(uid);
                    relatedUser.received.delete(uid);
                    metrics.blockedViolationsCleaned++;
                }
                continue; 
            }
        }

        // --- 2.2 AMISTADES (Bidireccionalidad) ---
        for (const friendUid of user.friends) {
            const friendNode = userMap.get(friendUid);
            if (!friendNode) continue; 
            if (user.blocked.has(friendUid) || friendNode.blocked.has(uid)) continue; 

            if (!friendNode.friends.has(uid)) {
                if (RELATION_MODE === "conservative") {
                    console.log(`[REMOVE] removed ghost friendship ${uid} -> ${friendUid} (Unreturned)`);
                    user.friends.delete(friendUid);
                    metrics.removedGhostFriendships++;
                } else if (RELATION_MODE === "aggressive") {
                    console.log(`[FIX] repaired friendship ${friendNode.id} <-> ${uid} (Forced mutual)`);
                    friendNode.friends.add(uid);
                    friendNode.sent.delete(uid);
                    friendNode.received.delete(uid);
                    user.sent.delete(friendUid);
                    user.received.delete(friendUid);
                    metrics.repairedFriendships++;
                }
            }
        }

        // --- 2.3 SOLICITUDES ENVIADAS (Ghost y Cruces) ---
        for (const targetUid of user.sent) {
            const targetNode = userMap.get(targetUid);
            if (!targetNode) continue;
            if (user.blocked.has(targetUid) || targetNode.blocked.has(uid)) continue;

            const targetHasMyRequest = targetNode.received.has(uid);
            
            if (user.friends.has(targetUid) && targetNode.friends.has(uid)) {
                console.log(`[REMOVE] removed invalid request ${uid} -> ${targetUid}. (Already friends)`);
                user.sent.delete(targetUid);
                targetNode.received.delete(uid);
                metrics.removedGhostRequests++;
                continue;
            }

            if (!targetHasMyRequest) {
                console.log(`[REMOVE] removed ghost request ${uid} -> ${targetUid} (Target lacked Received)`);
                user.sent.delete(targetUid);
                metrics.removedGhostRequests++;
            }

            if (targetNode.sent.has(uid)) {
                console.log(`[FIX] resolved cross-requests. Created friendship: ${uid} <-> ${targetUid}`);
                user.friends.add(targetUid);
                targetNode.friends.add(uid);
                
                user.sent.delete(targetUid);
                user.received.delete(targetUid);
                targetNode.sent.delete(uid);
                targetNode.received.delete(uid);
                
                metrics.crossRequestsResolved++;
            }
        }

        if (
            user.orig.friendsCount !== user.friends.size ||
            user.orig.sentCount !== user.sent.size ||
            user.orig.receivedCount !== user.received.size
        ) {
            if (user.friends.size <= user.orig.friendsCount && user.sent.size <= user.orig.sentCount) {
                metrics.removedDuplicates++;
            }
            user.needsUpdate = true;
        } else {
            metrics.skippedConsistent++;
        }
    }

    if (DRY_RUN) {
        console.log(`\n===========================================`);
        console.log(`🛑 DRY RUN (SIMULACIÓN FINALIZADA)`);
        let mutationsCount = 0;
        for (const [uid, user] of userMap.entries()) {
            if(user.needsUpdate) mutationsCount++;
        }
        console.log(`⚠️ Documentos candidatos a reparación mutante: ${mutationsCount}`);
    } else {
        console.log(`\n===========================================`);
        console.log(`💾 DRY RUN DESACTIVADO. PROTECCIÓN OPTIMISTIC LOCKING ACTIVA.`);
        
        const batches = [];
        let curBatch = db.batch();
        let opsCounter = 0;

        for (const [uid, user] of userMap.entries()) {
            if (user.needsUpdate) {
                curBatch.update(user.ref, {
                    friends: Array.from(user.friends),
                    friendRequestsSent: Array.from(user.sent),
                    friendRequestsReceived: Array.from(user.received),
                    lastUpdatedAt: admin.firestore.FieldValue.serverTimestamp()
                }, { lastUpdateTime: user.updateTime }); 

                opsCounter++;

                if (opsCounter === 450) {
                    batches.push(curBatch);
                    curBatch = db.batch();
                    opsCounter = 0;
                }
            }
        }

        if (opsCounter > 0) batches.push(curBatch);

        console.log(`Aguardando confirmación de ${batches.length} ciclos batch...`);
        for (let i = 0; i < batches.length; i++) {
            try {
                await batches[i].commit();
                console.log(`  ✅ Batch ${i+1}/${batches.length} completado.`);
            } catch (error) {
                if (error.message.includes('FAILED_PRECONDITION')) {
                    console.error(`  ❌ Batch ${i+1} bloqueado parcialmente: Concurrencia detectada`);
                    metrics.concurrencyRejections++;
                } else {
                    console.error(`  ❌ Error Crítico Batch ${i+1}:`, error.message);
                }
            }
        }
    }

    console.log(`\n📊 RESULTADOS FINALES DE SANITIZACIÓN:`);
    console.log(`   - Usuarios totales evaluados:          ${usersSnapshot.size}`);
    console.log(`   - [SKIP] Usuarios ya consistentes:     ${metrics.skippedConsistent}`);
    console.log(`   - [FIX] Peticiones cruzadas reparadas: ${metrics.crossRequestsResolved}`);
    console.log(`   - [FIX] Amistades rotas reparadas:     ${metrics.repairedFriendships}`);
    console.log(`   - [REMOVE] Relaciones fantasma borrad: ${metrics.removedGhostFriendships}`);
    console.log(`   - [REMOVE] Solicitudes inválidas/dead: ${metrics.removedGhostRequests}`);
    console.log(`   - [REMOVE] Residuos por Duplicación:   ${metrics.removedDuplicates}`);
    console.log(`   - [BLOCK] Violaciones de Privacy block:${metrics.blockedViolationsCleaned}`);
    if (!DRY_RUN) console.log(`   - 🛡️ Protegidos por Concurrencia:      ${metrics.concurrencyRejections}`);
    console.log(`======================================================\n`);
    
    process.exit(0);
}

sanitizeDatabase().catch(err => {
    console.error("❌ Fallo Crítico General:", err);
    process.exit(1);
});
