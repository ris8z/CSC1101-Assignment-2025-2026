import os

try:
    import matplotlib.pyplot as plt
    HAS_MATPLOTLIB = True
except ImportError:
    HAS_MATPLOTLIB = False

def plot_experiment_1(x_stockers, y_picker_wait, y_stocker_wait):
    if not HAS_MATPLOTLIB:
        return

    fig, ax1 = plt.subplots(figsize=(10, 6))

    color1 = 'tab:blue'
    ax1.set_xlabel('Number of Stockers')
    ax1.set_ylabel('AVG Picker Wait (ticks)', color=color1)
    ax1.plot(x_stockers, y_picker_wait, marker='o', color=color1, linewidth=2, label='Picker Wait')
    ax1.tick_params(axis='y', labelcolor=color1)
    ax1.grid(True, linestyle='--', alpha=0.6)

    ax2 = ax1.twinx()  
    color2 = 'tab:red'
    ax2.set_ylabel('AVG Stocker Wait (ticks)', color=color2)  
    ax2.plot(x_stockers, y_stocker_wait, marker='s', color=color2, linewidth=2, linestyle='--', label='Stocker Wait')
    ax2.tick_params(axis='y', labelcolor=color2)

    plt.title('Experiment 1: How many stockers?')
    fig.tight_layout()
    
    lines1, labels1 = ax1.get_legend_handles_labels()
    lines2, labels2 = ax2.get_legend_handles_labels()
    ax1.legend(lines1 + lines2, labels1 + labels2, loc='upper left')

    filename = 'exp1.png'
    output_dir = os.path.dirname(__file__) 
    filepath = os.path.join(output_dir, filename)
    plt.savefig(filepath)

def plot_experiment_2(x_stockers, y_no_breaks, y_breaks):
    if not HAS_MATPLOTLIB:
        return

    fig, ax = plt.subplots(figsize=(10, 6))

    ax.set_xlabel('Number of Stockers')
    ax.set_ylabel('Throughput (Total Picks Completed)')
    
    ax.plot(x_stockers, y_no_breaks, marker='X', color='tab:red', linewidth=2, label='NO Breaks (Starvation)')
    ax.plot(x_stockers, y_breaks, marker='^', color='tab:green', linewidth=2, label='WITH Breaks (Recovery)')
    
    ax.grid(True, linestyle='--', alpha=0.6)
    plt.title('Experiment 2: Stocker breaks vs no breaks?')
    ax.legend(loc='center right')
    fig.tight_layout()

    filename = 'exp2.png'
    output_dir = os.path.dirname(__file__) 
    filepath = os.path.join(output_dir, filename)
    plt.savefig(filepath)


def plot_experiment_3(x_trolleys, y_picks, y_p_wait, y_s_wait):
    if not HAS_MATPLOTLIB:
        return

    y_p_wait_cleaned = [val if val != 0 else None for val in y_p_wait]
    fig, ax1 = plt.subplots(figsize=(10, 6))

    color1 = 'tab:green'
    ax1.set_xlabel('Number of Trolleys (K-Factor)')
    ax1.set_ylabel('Throughput (Total Picks)', color=color1)
    ax1.plot(x_trolleys, y_picks, marker='o', color=color1, linewidth=3, label='Throughput (Picks)')
    ax1.tick_params(axis='y', labelcolor=color1)
    ax1.grid(True, linestyle='--', alpha=0.6)

    ax2 = ax1.twinx()  
    color2 = 'tab:blue'
    color3 = 'tab:orange'
    ax2.set_ylabel('Average Wait (ticks)', color='black')  
    ax2.plot(x_trolleys, y_p_wait_cleaned, marker='s', color=color2, linewidth=2, linestyle='-', label='Picker Wait')
    ax2.plot(x_trolleys, y_s_wait, marker='^', color=color3, linewidth=2, linestyle='--', label='Stocker Wait')
    ax2.tick_params(axis='y', labelcolor='black')

    plt.title('Experiment 3: How many trolley?')
    fig.tight_layout()
    
    lines1, labels1 = ax1.get_legend_handles_labels()
    lines2, labels2 = ax2.get_legend_handles_labels()
    ax1.legend(lines1 + lines2, labels1 + labels2, loc='center right')

    filename = 'exp3.png'
    output_dir = os.path.dirname(__file__) 
    filepath = os.path.join(output_dir, filename)
    plt.savefig(filepath)

