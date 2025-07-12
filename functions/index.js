const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();

const db = admin.database();

/**
 * Agrega tickets a un jugador y los convierte en pases si se alcanza la meta.
 */
exports.addTickets = functions.https.onCall(async (data, context) => {
    try {
        const { playerId, amount } = data;

        if (!playerId || typeof playerId !== 'string' || playerId.length < 5) {
            throw new functions.https.HttpsError('invalid-argument', 'Se requiere un ID de jugador válido.');
        }

        if (typeof amount !== 'number' || amount < 0) {
            throw new functions.https.HttpsError('invalid-argument', 'La cantidad debe ser un número no negativo.');
        }

        const userRef = db.ref(`users/${playerId}`);

        const result = await userRef.transaction((currentData) => {
            // Si el usuario no existe, crea una estructura base.
            const currentUser = currentData || {
                playerId: playerId,
                tickets: 0,
                passes: 0,
            };

            // Asegurar que los campos numéricos no sean nulos.
            currentUser.tickets = (currentUser.tickets || 0) + amount;
            currentUser.passes = currentUser.passes || 0;

            const TICKETS_PER_PASS = 1000;
            if (currentUser.tickets >= TICKETS_PER_PASS) {
                const newPasses = Math.floor(currentUser.tickets / TICKETS_PER_PASS);
                currentUser.passes += newPasses;
                currentUser.tickets %= TICKETS_PER_PASS; // Tickets restantes
            }

            return currentUser;
        });

        if (!result.committed) {
            throw new functions.https.HttpsError('aborted', 'La transacción no se pudo completar. Intenta de nuevo.');
        }

        const userData = result.snapshot.val();
        const message = amount > 0 && userData.passes > (userData.passes - Math.floor((userData.tickets + amount) / 1000))
            ? 'Tickets agregados y pase ganado!'
            : 'Tickets agregados correctamente';

        return {
            success: true,
            message,
            user: userData,
        };

    } catch (error) {
        console.error('Error en addTickets:', error);
        if (error instanceof functions.https.HttpsError) {
            throw error;
        }
        throw new functions.https.HttpsError('internal', 'Ocurrió un error interno.', error.message);
    }
});

/**
 * Crea un nuevo usuario en la base de datos si no existe.
 */
exports.initializeUser = functions.https.onCall(async (data, context) => {
    try {
        const { playerId } = data;

        if (!playerId || typeof playerId !== 'string' || playerId.length < 5) {
            throw new functions.https.HttpsError('invalid-argument', 'Se requiere un ID de jugador válido.');
        }

        const userRef = db.ref(`users/${playerId}`);
        const snapshot = await userRef.once('value');

        if (!snapshot.exists()) {
            const newUser = {
                playerId,
                tickets: 0,
                passes: 0,
            };
            await userRef.set(newUser);
            return {
                success: true,
                message: 'Usuario inicializado correctamente',
                user: newUser,
            };
        } else {
            return {
                success: true,
                message: 'El usuario ya existe',
                user: snapshot.val(),
            };
        }
    } catch (error) {
        console.error('Error en initializeUser:', error);
        if (error instanceof functions.https.HttpsError) {
            throw error;
        }
        throw new functions.https.HttpsError('internal', 'Ocurrió un error interno.', error.message);
    }
});

// Nota: La función 'sendTicketsToId' fue eliminada porque no hay una
// interfaz en la UI para usarla y su lógica es cubierta por 'addTickets'.
// Si la necesitas en el futuro, puedes volver a añadirla.