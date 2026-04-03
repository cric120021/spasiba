import { ImageResponse } from "next/og";

export const size = {
  width: 512,
  height: 512
};
export const contentType = "image/png";

export default function Icon() {
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
          fontSize: 196,
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
