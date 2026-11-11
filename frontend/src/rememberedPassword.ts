const storageKey = "househunt.password";

// Storage can be missing or blocked, so every access is guarded.
export function loadRememberedPassword(): string | null {
  try {
    return window.localStorage.getItem(storageKey);
  } catch {
    return null;
  }
}

export function saveRememberedPassword(password: string): void {
  try {
    window.localStorage.setItem(storageKey, password);
  } catch {
    // Remembering is a convenience; ignore failures.
  }
}

export function forgetRememberedPassword(): void {
  try {
    window.localStorage.removeItem(storageKey);
  } catch {
    // Nothing to do.
  }
}
