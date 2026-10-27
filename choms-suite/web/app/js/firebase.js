/**
 * firebase.js - single place where the Firebase SDK is initialised (Data layer).
 * Modular SDK loaded from Google's CDN so no build step is needed.
 */
import { initializeApp, deleteApp } from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js';
import {
  getAuth, onAuthStateChanged, signInWithEmailAndPassword, signOut,
  sendPasswordResetEmail, createUserWithEmailAndPassword, deleteUser
} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js';
import {
  getFirestore, collection, doc, getDoc, getDocFromServer, getDocs, setDoc, deleteDoc, query, where, writeBatch
} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js';
import { getFunctions, httpsCallable } from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-functions.js';
import { firebaseConfig, isConfigured } from './firebase-config.js';

export const configured = isConfigured();
const app = configured ? initializeApp(firebaseConfig) : null;
export const auth = app ? getAuth(app) : null;
export const db = app ? getFirestore(app) : null;
// Cloud Functions run in the same region as Firestore (see functions/index.js)
const functions = app ? getFunctions(app, 'africa-south1') : null;

export {
  onAuthStateChanged, signInWithEmailAndPassword, signOut, sendPasswordResetEmail, createUserWithEmailAndPassword, deleteUser,
  collection, doc, getDoc, getDocFromServer, getDocs, setDoc, deleteDoc, query, where, writeBatch
};

/** Admin only: deletes a person's login + profile and logs it (Cloud Function `deleteUser`). */
export const deleteUserAccount = (uid) => httpsCallable(functions, 'deleteUser')({ uid });

/** True when the error means the deleteUser function is not deployed / not reachable (e.g. Spark plan). */
export const isFunctionMissing = (e) =>
  e?.code === 'functions/not-found' || (e?.code === 'functions/internal' && /^internal$/i.test(e?.message || ''));

/**
 * Creates a Firebase Auth account WITHOUT signing the current admin out.
 * A temporary secondary app instance is used, then discarded.
 * @returns {Promise<string>} the new user's uid
 */
export async function createAuthUser(email, password) {
  const secondary = initializeApp(firebaseConfig, `secondary-${Date.now()}`);
  try {
    const secAuth = getAuth(secondary);
    const cred = await createUserWithEmailAndPassword(secAuth, email, password);
    await signOut(secAuth);
    return cred.user.uid;
  } finally {
    await deleteApp(secondary);
  }
}
