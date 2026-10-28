"use client";
import { useEffect, useRef, useState } from "react";
import { adminFetch } from "@/services/adminApi";
import "./MenuContentDetails.css";
type Report = {
  startedAt: string;
  metrics: {
    name: string;
    page: string;
    samples: number;
    unit: string;
    p75UpperBound: number;
    p95UpperBound: number;
  }[];
};
export default function StorefrontExperienceReport() {
  const [report, setReport] = useState<Report | null>(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(false),
    request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), []);
  async function load() {
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    setBusy(true);
    setError(false);
    try {
      const response = await adminFetch(
        "/api/admin/storefront/performance",
        "",
        {
          signal: AbortSignal.any([
            controller.signal,
            AbortSignal.timeout(10000),
          ]),
        },
      );
      if (!response.ok) throw Error("Performance unavailable");
      const value = (await response.json()) as Report;
      if (!Array.isArray(value.metrics) || value.metrics.length > 140)
        throw Error("Invalid performance response");
      if (!controller.signal.aborted) setReport(value);
    } catch {
      if (!controller.signal.aborted) setError(true);
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  return (
    <details className="admin-menu-quality">
      <summary>Storefront experience · sampled performance</summary>
      <p>
        Anonymous samples from the current backend instance since{" "}
        {report ? new Date(report.startedAt).toLocaleString() : "startup"}.
        Counts reset when this instance restarts; other instances are separate.
        Only sampled client documents contribute.
      </p>
      <button type="button" disabled={busy} onClick={() => void load()}>
        {busy ? "Loading performance…" : "Refresh performance"}
      </button>
      {error && <p role="alert">Performance could not be loaded. Try again.</p>}
      {report &&
        (!report.metrics.length ? (
          <p>
            No timing samples yet. Browse the customer app to collect samples
            over time.
          </p>
        ) : (
          <>
            <p>
              P75 and P95 are histogram bucket upper bounds. Fewer than 20
              samples should be treated as directional.
            </p>
            <div className="admin-performance-table">
              <table>
                <caption>Sampled route timings · current instance</caption>
                <thead>
                  <tr>
                    <th>Page</th>
                    <th>Metric</th>
                    <th>Samples</th>
                    <th>P75 ≤</th>
                    <th>P95 ≤</th>
                  </tr>
                </thead>
                <tbody>
                  {report.metrics.map((metric) => (
                    <tr key={`${metric.page}:${metric.name}`}>
                      <td>{metric.page}</td>
                      <td>{metric.name.replaceAll("_", " ")}</td>
                      <td>
                        {metric.samples}
                        {metric.samples < 20 ? " · few samples" : ""}
                      </td>
                      <td>
                        {metric.p75UpperBound}
                        {metric.unit === "ms" ? " ms" : ""}
                      </td>
                      <td>
                        {metric.p95UpperBound}
                        {metric.unit === "ms" ? " ms" : ""}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        ))}
    </details>
  );
}
