import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./app/**/*.{ts,tsx}", "./components/**/*.{ts,tsx}", "./lib/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        bluex: "#1565D8",
        bluedeep: "#0B49B0",
        orange: "#FF6A1A",
        orangesoft: "#FFF1E8",
        ink: "#142033",
        paper: "#F4F7FB",
        muted: "#5C6B80",
        line: "#E6EDF5",
        ok: "#0F7A45",
        danger: "#B42318",
      },
      boxShadow: {
        card: "0 10px 30px rgba(11, 73, 176, 0.08)",
      },
    },
  },
  plugins: [],
};

export default config;
