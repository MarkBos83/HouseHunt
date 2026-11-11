import type { EncryptedPayload, SiteData } from "./types";

function decodeBase64(text: string): ArrayBuffer {
  const binary = atob(text);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index);
  return bytes.buffer as ArrayBuffer;
}

async function deriveDecryptionKey(password: string, payload: EncryptedPayload): Promise<CryptoKey> {
  const passwordKey = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, [
    "deriveKey",
  ]);
  return crypto.subtle.deriveKey(
    { name: "PBKDF2", salt: decodeBase64(payload.salt), iterations: payload.iterations, hash: "SHA-256" },
    passwordKey,
    { name: "AES-GCM", length: 256 },
    false,
    ["decrypt"],
  );
}

/** Rejects when the password is wrong: AES-GCM fails its integrity check. */
export async function decryptPayload(payload: EncryptedPayload, password: string): Promise<SiteData> {
  const key = await deriveDecryptionKey(password, payload);
  const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: decodeBase64(payload.iv) }, key, decodeBase64(payload.data));
  return JSON.parse(new TextDecoder().decode(plain)) as SiteData;
}

export function readEncryptedPayload(): EncryptedPayload | null {
  const element = document.getElementById("encrypted-data");
  if (!element?.textContent) return null;
  return JSON.parse(element.textContent) as EncryptedPayload;
}
