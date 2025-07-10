const functions = require('firebase-functions');
const admin = require('firebase-admin');

// Inicializar Firebase Admin
admin.initializeApp();

const db = admin.database();

// Función para agregar tickets
exports.addTickets = functions.https.onCall(async (data, context) => {
  try {
    const { playerId, amount } = data;

    console.log('addTickets called with:', { playerId, amount });

    if (!playerId || typeof playerId !== 'string') {
      throw new functions.https.HttpsError('invalid-argument', 'Player ID is required and must be a string');
    }

    if (typeof amount !== 'number' || amount < 0) {
      throw new functions.https.HttpsError('invalid-argument', 'Amount must be a non-negative number');
    }

    const userRef = db.ref(`users/${playerId}`);

    // Usar transacción para actualizar de forma segura
    const result = await userRef.transaction((currentData) => {
      const currentUser = currentData || { playerId, tickets: 0, passes: 0 };

      // Agregar tickets
      currentUser.tickets = (currentUser.tickets || 0) + amount;

      // Verificar si se debe convertir a pase (cada 1000 tickets = 1 pase)
      const TICKETS_PER_PASS = 1000;
      if (currentUser.tickets >= TICKETS_PER_PASS) {
        const newPasses = Math.floor(currentUser.tickets / TICKETS_PER_PASS);
        currentUser.passes = (currentUser.passes || 0) + newPasses;
        currentUser.tickets = currentUser.tickets % TICKETS_PER_PASS; // Tickets restantes
      }

      // Asegurar que playerId esté presente
      currentUser.playerId = playerId;

      return currentUser;
    });

    if (!result.committed) {
      throw new functions.https.HttpsError('aborted', 'Transaction failed');
    }

    const userData = result.snapshot.val();
    const message = amount > 0 && userData.passes > (userData.passes - Math.floor((userData.tickets + amount) / 1000))
      ? 'Tickets agregados y pase ganado!'
      : 'Tickets agregados correctamente';

    console.log('addTickets result:', { userData, message });

    return {
      success: true,
      message,
      user: userData
    };

  } catch (error) {
    console.error('Error in addTickets:', error);
    throw new functions.https.HttpsError('internal', error.message);
  }
});

// Función para enviar tickets a otro jugador
exports.sendTicketsToId = functions.https.onCall(async (data, context) => {
  try {
    const { playerId, amount } = data;

    console.log('sendTicketsToId called with:', { playerId, amount });

    if (!playerId || typeof playerId !== 'string') {
      throw new functions.https.HttpsError('invalid-argument', 'Player ID is required and must be a string');
    }

    if (typeof amount !== 'number' || amount <= 0) {
      throw new functions.https.HttpsError('invalid-argument', 'Amount must be a positive number');
    }

    const userRef = db.ref(`users/${playerId}`);

    // Usar transacción para agregar tickets de forma segura
    const result = await userRef.transaction((currentData) => {
      const currentUser = currentData || { playerId, tickets: 0, passes: 0 };

      // Agregar tickets
      currentUser.tickets = (currentUser.tickets || 0) + amount;

      // Verificar conversión a pases
      const TICKETS_PER_PASS = 1000;
      if (currentUser.tickets >= TICKETS_PER_PASS) {
        const newPasses = Math.floor(currentUser.tickets / TICKETS_PER_PASS);
        currentUser.passes = (currentUser.passes || 0) + newPasses;
        currentUser.tickets = currentUser.tickets % TICKETS_PER_PASS;
      }

      currentUser.playerId = playerId;

      return currentUser;
    });

    if (!result.committed) {
      throw new functions.https.HttpsError('aborted', 'Transaction failed');
    }

    console.log('sendTicketsToId result:', result.snapshot.val());

    return {
      success: true,
      message: 'Tickets enviados correctamente',
      user: result.snapshot.val()
    };

  } catch (error) {
    console.error('Error in sendTicketsToId:', error);
    throw new functions.https.HttpsError('internal', error.message);
  }
});

// Función para crear usuario si no existe
exports.initializeUser = functions.https.onCall(async (data, context) => {
  try {
    const { playerId } = data;

    console.log('initializeUser called with:', { playerId });

    if (!playerId || typeof playerId !== 'string') {
      throw new functions.https.HttpsError('invalid-argument', 'Player ID is required and must be a string');
    }

    const userRef = db.ref(`users/${playerId}`);
    const snapshot = await userRef.once('value');

    if (!snapshot.exists()) {
      const newUser = {
        playerId,
        tickets: 0,
        passes: 0
      };

      await userRef.set(newUser);

      console.log('User initialized:', newUser);

      return {
        success: true,
        message: 'Usuario inicializado correctamente',
        user: newUser
      };
    } else {
      console.log('User already exists:', snapshot.val());
      return {
        success: true,
        message: 'Usuario ya existe',
        user: snapshot.val()
      };
    }

  } catch (error) {
    console.error('Error in initializeUser:', error);
    throw new functions.https.HttpsError('internal', error.message);
  }
});