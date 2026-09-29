import { stopPreview } from "./preview-process.mjs";
const port = Number(process.env.PORT || 4173);
const outcome = await stopPreview(port);
const messages = {
  stopped: `StockFlow preview on port ${port} stopped.`,
  "not-running": `No registered StockFlow preview is running on port ${port}.`,
  "identity-mismatch":
    "The saved process identity no longer matches. No process was stopped.",
  "still-running":
    "The preview has not stopped yet. Use Ctrl-C in its terminal; no forced termination was attempted.",
};
console.log(messages[outcome]);
if (outcome === "identity-mismatch" || outcome === "still-running")
  process.exitCode = 1;
