@import java.util.stream.IntStream

@param long mask
@param String domain

<svg width="40" height="40"
     style="background-image: url('/site/${domain}/favicon'); background-repeat: no-repeat; background-size: 16px 16px; background-position: center; ">
    <circle
            cx="20"
            cy="20"
            r="16"
            fill="none"
            stroke="#eee"
            stroke-width="2"
    />
@for (int bit : IntStream.range(0, 56).filter(bit -> (mask & (1L << bit)) != 0).toArray())
            <line
                    x1="${20 + 15*Math.sin(2 * Math.PI * bit / 56.)}"
                    y1="${20 - 15*Math.cos(2 * Math.PI * bit / 56.)}"
                    x2="${20 + 17*Math.sin(2 * Math.PI * bit / 56.)}"
                    y2="${20 - 17*Math.cos(2 * Math.PI * bit / 56.)}"
                    stroke="#444"
                    stroke-width="2"
            />
@endfor
</svg>