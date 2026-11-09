import Image from "next/image";

export function BrandLogo({ className = "", size = "md" }: { className?: string; size?: "sm" | "md" }) {
  const frame = size === "sm" ? "w-24 max-w-[58%] sm:w-28" : "w-32 max-w-[64%] sm:w-36 lg:mx-0 lg:w-56 lg:max-w-full";
  return (
    <span className={`mx-auto block overflow-hidden ${frame} ${className}`}>
      <Image
        src="/images/xander-tech-logo.jpg"
        alt="Xander Tech LLC"
        width={4000}
        height={4000}
        priority
        sizes="(min-width: 1024px) 256px, 160px"
        style={{ width: "100%", height: "auto", maxWidth: "100%" }}
        className="block h-auto w-full max-w-full"
      />
    </span>
  );
}
