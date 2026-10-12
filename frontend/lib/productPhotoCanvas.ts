import type { Area } from "react-easy-crop";
/** Square output: omit crop for a full-image fit; supply crop for fill. */
export function renderProductPhoto(
  image: HTMLImageElement,
  rotation: number,
  area?: Area,
): HTMLCanvasElement {
  const radians = (rotation * Math.PI) / 180;
  const width = Math.round(
    Math.abs(Math.cos(radians) * image.width) +
      Math.abs(Math.sin(radians) * image.height),
  );
  const height = Math.round(
    Math.abs(Math.sin(radians) * image.width) +
      Math.abs(Math.cos(radians) * image.height),
  );
  const rotated = document.createElement("canvas");
  rotated.width = width;
  rotated.height = height;
  const ctx = rotated.getContext("2d");
  if (!ctx) throw new Error("Image editor unavailable.");
  ctx.translate(width / 2, height / 2);
  ctx.rotate(radians);
  ctx.drawImage(image, -image.width / 2, -image.height / 2);
  const output = document.createElement("canvas");
  output.width = 800;
  output.height = 800;
  const target = output.getContext("2d");
  if (!target) throw new Error("Image editor unavailable.");
  target.fillStyle = "#fff";
  target.fillRect(0, 0, 800, 800);
  if (area)
    target.drawImage(
      rotated,
      area.x,
      area.y,
      area.width,
      area.height,
      0,
      0,
      800,
      800,
    );
  else {
    const scale = Math.min(800 / width, 800 / height);
    const w = width * scale,
      h = height * scale;
    target.drawImage(rotated, (800 - w) / 2, (800 - h) / 2, w, h);
  }
  return output;
}
