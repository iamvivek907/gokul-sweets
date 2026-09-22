export default function Loading() {
    return (
        <div className="fixed inset-0 z-[9999] flex min-h-screen items-center justify-center bg-[#fffaf5]">
            <div className="flex flex-col items-center">

                <div className="flex h-28 w-28 items-center justify-center overflow-hidden rounded-3xl bg-white shadow-lg">
                    <img
                        src="/logo.png"
                        alt="Gokul Sweets"
                        className="h-full w-full object-contain p-3"
                    />
                </div>

                <h1 className="mt-6 text-xl font-bold text-[#7a3e1d]">
                    Gokul Sweets
                </h1>

                <p className="mt-1 text-sm text-slate-500">
                    Sweets • Snacks • Restaurants
                </p>

                <div className="mt-6 h-1.5 w-32 overflow-hidden rounded-full bg-[#ead8c8]">
                    <div className="h-full w-1/2 animate-pulse rounded-full bg-[#8b4513]" />
                </div>

                <p className="mt-4 text-xs text-slate-400">
                    Loading...
                </p>

            </div>
        </div>
    );
}