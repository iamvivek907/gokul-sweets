export default function PickupSlotSkeleton() {

    return (
        <div
            className="
                space-y-3
            "
        >

            {[1, 2, 3, 4].map(
                item => (

                    <div
                        key={
                            item
                        }
                        className="
                            h-24
                            animate-pulse
                            rounded-2xl
                            border
                            border-[#eadfd6]
                            bg-white
                        "
                    />

                )
            )}

        </div>
    );
}