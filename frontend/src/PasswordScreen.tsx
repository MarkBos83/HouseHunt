import { useState, type FormEvent } from "react";

interface PasswordScreenProps {
  busy: boolean;
  errorMessage: string | null;
  onSubmit: (password: string, remember: boolean) => void;
}

export function PasswordScreen({ busy, errorMessage, onSubmit }: PasswordScreenProps) {
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(false);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (password) onSubmit(password, remember);
  }

  return (
    <main className="password-screen">
      <form onSubmit={handleSubmit}>
        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          autoFocus
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
        <label className="remember">
          <input type="checkbox" checked={remember} onChange={(event) => setRemember(event.target.checked)} />
          Remember on this device
        </label>
        <button type="submit" disabled={busy || !password}>
          {busy ? "Unlocking…" : "Unlock"}
        </button>
        {errorMessage && <p role="alert" className="error">{errorMessage}</p>}
      </form>
    </main>
  );
}
