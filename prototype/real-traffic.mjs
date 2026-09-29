export function realTraffic(fetchLocal = fetch) {
  async function request(path, method = "GET") {
    const response = await fetchLocal(path, { method, cache: "no-store" });
    if (!response.ok) throw new Error(`Traffic control returned ${response.status}`);
    return response.json();
  }
  return {
    status: () => request("/lab/traffic"),
    start(rate) {
      if (!Number.isInteger(rate) || rate < 1 || rate > 10000) {
        throw new RangeError("Real traffic rate must be 1–10,000 requests/s");
      }
      return request(`/lab/traffic/start?rate=${rate}&seconds=0&concurrency=256`, "POST");
    },
    stop: () => request("/lab/traffic/stop", "POST"),
    addInstance: () => request("/lab/traffic/add-instance", "POST"),
    removeInstance: () => request("/lab/traffic/remove-instance", "POST"),
  };
}
