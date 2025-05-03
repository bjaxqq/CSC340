#!/bin/bash

# MacOS Version
# Usage: ./rtt_monitor.sh <IP_ADDRESS> <REGION_NAME>
# Dependencies: ping, bc, gnuplot

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <IP_ADDRESS> <REGION_NAME>"
    exit 1
fi

IP=$1
REGION=$2

# Create output directories
mkdir -p data graphs

# File paths
DATA_FILE="data/rtt_${REGION}.log"
PLOT_FILE="graphs/rtt_plot_${REGION}.png"

# Initialize arrays
declare -a sample_rtt
declare -a estimated_rtt
declare -a timeout_interval

# TCP EWMA parameters
ALPHA=0.125
BETA=0.25

# Get initial RTT
INITIAL_RTT=$(ping -c 1 $IP | awk '/time=/ {print $7}' | cut -d= -f2)
if [ -z "$INITIAL_RTT" ]; then
    INITIAL_RTT=50  # Default fallback
    echo "Warning: Initial ping failed. Using default RTT=$INITIAL_RTT ms"
fi

# Initialize first values
sample_rtt[0]=$INITIAL_RTT
estimated_rtt[0]=$INITIAL_RTT
timeout_interval[0]=$(echo "$INITIAL_RTT * 4" | bc)

echo "Monitoring RTT for $IP ($REGION)..."

for ((i=1; i<=20; i++)); do
    # Measure SampleRTT
    current_rtt=$(ping -c 1 $IP | awk '/time=/ {print $7}' | cut -d= -f2)
    if [ -z "$current_rtt" ]; then
        current_rtt=${estimated_rtt[$((i-1))]}  # Fallback to last estimated RTT
        echo "Warning: Ping failed at iteration $i. Using last EstimatedRTT"
    fi

    sample_rtt[$i]=$current_rtt

    # Calculate EstimatedRTT
    prev_estimated=${estimated_rtt[$((i-1))]}
    current_estimated=$(echo "scale=4; (1 - $ALPHA) * $prev_estimated + $ALPHA * $current_rtt" | bc)
    estimated_rtt[$i]=$current_estimated

    # Calculate DevRTT and TimeoutInterval
    dev_rtt=$(echo "scale=4; (1 - $BETA) * ${timeout_interval[$((i-1))]} / 4 + $BETA * sqrt((${sample_rtt[$i]} - ${estimated_rtt[$i]})^2)" | bc)
    timeout_interval[$i]=$(echo "scale=4; $current_estimated + 4 * $dev_rtt" | bc)

    printf "Iter %02d: SampleRTT=%-6.2f ms | EstimatedRTT=%-6.2f ms | Timeout=%-6.2f ms\n" \
           $i ${sample_rtt[$i]} ${estimated_rtt[$i]} ${timeout_interval[$i]}
    sleep 10
done

# Generate plot data
echo "Time(sec) SampleRTT EstimatedRTT TimeoutInterval" > $DATA_FILE
for ((i=0; i<=20; i++)); do
    echo "$((i*10)) ${sample_rtt[$i]} ${estimated_rtt[$i]} ${timeout_interval[$i]}" >> $DATA_FILE
done

# Plot with gnuplot
gnuplot <<- EOF
    set terminal pngcairo enhanced font "Arial,12"
    set output '$PLOT_FILE'
    set title "RTT Analysis: $REGION ($IP)"
    set xlabel "Time (seconds)"
    set ylabel "RTT (milliseconds)"
    set grid
    set key top left
    plot "$DATA_FILE" using 1:2 with linespoints lw 2 title "SampleRTT", \
         "$DATA_FILE" using 1:3 with linespoints lw 2 title "EstimatedRTT (EWMA)", \
         "$DATA_FILE" using 1:4 with linespoints lw 2 title "TimeoutInterval"
EOF

echo -e "\nResults saved:"
echo "- Data: $DATA_FILE"
echo "- Plot: $PLOT_FILE"