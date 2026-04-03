import { ImageResponse } from "next/og";

export const size = {
  width: 180,
  height: 180
};
export const contentType = "image/png";

export default function AppleIcon() {
  return new ImageResponse(
    (
      <div
        style={{
          height: "100%",
          width: "100%",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "linear-gradient(180deg, #06101d 0%, #020409 100%)",
          color: "#f8fafc",
          fontSize: 72,
          fontWeight: 700,
          letterSpacing: "-0.08em"
        }}
      >
        SP
      </div>
    ),
    size
  );
}
