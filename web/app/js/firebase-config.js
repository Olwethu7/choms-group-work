/**
 * Firebase project configuration.
 * Replace every YOUR_... value with the values from:
 *   Firebase Console > Project settings > General > Your apps > Web app > SDK setup and configuration
 *
 * NOTE: A Firebase web API key is an identifier, not a secret. Access is protected by
 * Firebase Authentication + the Firestore Security Rules in firebase/firestore.rules.
 */
export const firebaseConfig = {
  apiKey: 'AIzaSyB5fD34DG2KoOkzrPtJzwv7SzoYDYSOV64',
  authDomain: 'match-choms.firebaseapp.com',
  projectId: 'match-choms',
  storageBucket: 'match-choms.firebasestorage.app',
  messagingSenderId: '553270228784',
  appId: '1:553270228784:web:a1b2f48f6998edd239faf5'
};

export const isConfigured = () => !String(firebaseConfig.apiKey).startsWith('YOUR_');
