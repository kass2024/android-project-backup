import * as React from "react";
import { cn } from "@/lib/cn";

type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "orange" | "blue" | "ghost" | "outline";
};

export function Button({ className, variant = "orange", type = "button", ...props }: ButtonProps) {
  return (
    <button
      type={type}
      className={cn(
        "inline-flex h-12 w-full items-center justify-center rounded-2xl px-4 text-base font-bold transition active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-50",
        variant === "orange" && "bg-orange text-white shadow-card",
        variant === "blue" && "border border-bluex bg-white text-bluex",
        variant === "ghost" && "h-auto w-auto bg-transparent px-0 text-bluex",
        variant === "outline" && "border border-line bg-white text-ink",
        className,
      )}
      {...props}
    />
  );
}
