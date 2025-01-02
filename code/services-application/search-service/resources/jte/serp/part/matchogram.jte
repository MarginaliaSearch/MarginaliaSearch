@import java.util.stream.IntStream

@param long mask

<svg width="40" height="40">
    <circle
            cx="18"
            cy="18"
            r="16"
            fill="none"
            stroke="#eee"
            stroke-width="2"
    />
@for (int bit : IntStream.range(0, 56).filter(bit -> (mask & (1L << bit)) != 0).toArray())
            <line
                    x1="${18 + 15*Math.sin(2 * Math.PI * bit / 56.)}"
                    y1="${18 - 15*Math.cos(2 * Math.PI * bit / 56.)}"
                    x2="${18 + 17*Math.sin(2 * Math.PI * bit / 56.)}"
                    y2="${18 - 17*Math.cos(2 * Math.PI * bit / 56.)}"
                    stroke="#444"
                    stroke-width="2"
            />
@endfor
</svg>