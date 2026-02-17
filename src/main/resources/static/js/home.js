const homeRoot = document.querySelector(".home-neo");
if (homeRoot) {
    requestAnimationFrame(() => homeRoot.classList.add("home-ready"));

    const updatePointerGlow = (x, y) => {
        const normalizedX = x / Math.max(window.innerWidth, 1);
        const normalizedY = y / Math.max(window.innerHeight, 1);
        document.documentElement.style.setProperty("--mx", `${(normalizedX * 100).toFixed(2)}%`);
        document.documentElement.style.setProperty("--my", `${(normalizedY * 100).toFixed(2)}%`);
    };

    homeRoot.addEventListener("pointermove", (event) => {
        updatePointerGlow(event.clientX, event.clientY);
    });

    homeRoot.addEventListener("pointerleave", () => {
        document.documentElement.style.setProperty("--mx", "50%");
        document.documentElement.style.setProperty("--my", "35%");
    });
}
