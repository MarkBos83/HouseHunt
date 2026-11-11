import { useCallback, useEffect, useState } from "react";
import { decryptPayload, readEncryptedPayload } from "./crypto";
import { HomeTable } from "./HomeTable";
import { PasswordScreen } from "./PasswordScreen";
import { forgetRememberedPassword, loadRememberedPassword, saveRememberedPassword } from "./rememberedPassword";
import { StatusLine } from "./StatusLine";
import type { EncryptedPayload, SiteData } from "./types";

const wrongPasswordMessage = "Wrong password.";

async function unlock(payload: EncryptedPayload, password: string, remember: boolean): Promise<SiteData> {
  const data = await decryptPayload(payload, password);
  if (remember) saveRememberedPassword(password);
  return data;
}

export function App() {
  const [payload] = useState(readEncryptedPayload);
  const [data, setData] = useState<SiteData | null>(null);
  const [busy, setBusy] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const tryPassword = useCallback(
    async (password: string, remember: boolean) => {
      if (!payload) return;
      setBusy(true);
      setErrorMessage(null);
      try {
        setData(await unlock(payload, password, remember));
      } catch {
        forgetRememberedPassword();
        setErrorMessage(wrongPasswordMessage);
      } finally {
        setBusy(false);
      }
    },
    [payload],
  );

  useEffect(() => {
    const remembered = loadRememberedPassword();
    if (remembered) void tryPassword(remembered, false);
  }, [tryPassword]);

  if (!payload) return <p className="empty">No data in this page.</p>;
  if (!data) return <PasswordScreen busy={busy} errorMessage={errorMessage} onSubmit={tryPassword} />;

  const nowEpochSeconds = Math.floor(Date.now() / 1000);
  return (
    <main>
      <StatusLine data={data} nowEpochSeconds={nowEpochSeconds} />
      <HomeTable homes={data.homes} nowEpochSeconds={nowEpochSeconds} newBadgeHours={data.newBadgeHours} />
    </main>
  );
}
