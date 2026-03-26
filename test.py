import subprocess
import re
from appendix_graphs import test_plotter as plotter

CONFIG_FILE = 'warehouse.properties'

def read_original_config():
    with open(CONFIG_FILE, 'r') as f:
        return f.read()

def restore_config(original_content):
    with open(CONFIG_FILE, 'w') as f:
        f.write(original_content)

def update_properties(overrides):
    with open(CONFIG_FILE, 'r') as f:
        lines = f.readlines()
    
    with open(CONFIG_FILE, 'w') as f:
        for line in lines:
            modified = False
            for key, new_val in overrides.items():
                if line.startswith(f"{key}="):
                    f.write(f"{key}={new_val}\n")
                    modified = True
                    break
            if not modified:
                f.write(line)

def run_simulation():
    """
    Run just 1 simulation (let it run just for 3 sec then kill it)
    You need to set tick_ms to 1, otherwise the data captured is going to be too little

    RETURN a 3-tuple (pickers_average_wait_time, stockers_average_wait_time, number_of_picks_done)
    """
    out = ""
    try:
        result = subprocess.run(['java', '-cp', 'code', 'Warehouse'], capture_output=True, text=True, timeout=3)
        out = result.stdout
    except subprocess.TimeoutExpired as e:
        out = e.stdout if e.stdout else ""
        if isinstance(out, bytes):
            out = out.decode('utf-8', errors='ignore')

    if not out:
        print("No char was captured! Java probaly exploded or you did not compile try to run: make")
        return 0, 0, 0

    picker_ticks = re.findall(r'event=pick_done.*waited_ticks=(\d+)', out)
    stocker_ticks = re.findall(r'event=stocker_load.*waited_ticks=(\d+)', out)

    p_avg = sum(int(t) for t in picker_ticks) / len(picker_ticks) if picker_ticks else 0
    s_avg = sum(int(t) for t in stocker_ticks) / len(stocker_ticks) if stocker_ticks else 0
    p_cnt = len(picker_ticks)
    
    return p_avg, s_avg, p_cnt 

def experiment_1():
    """ 
    In this test we try to understand which is the right number of stockers that lead to good performance.
    We keep picker and trolleys fixed, and we change the number of stocker between 1 to 15 to see if waiting time decrease
    """
    base_settings = {
        'tick_ms': '1',
        'num_pickers': '5',
        'num_trolleys': '20'
    }
    update_properties(base_settings)

    stocker_counts = list(range(1, 16))
    picker_wait_results = []
    stocker_wait_results = []

    print("Starting Experiment 1:")

    # =================== running simulations ========================================
    print("fixed config: ", base_settings)
    print("variable config: {'num_stockers': '[1-15]'}")
    
    for s in stocker_counts:
        update_properties({'num_stockers': str(s)})
        p_avg, s_avg, _ = run_simulation()
        
        picker_wait_results.append(round(p_avg, 2))
        stocker_wait_results.append(round(s_avg, 2))
        
        print(f"Stockers: {s} | AVG Picker Wait: {p_avg} | AVG Stocker Wait: {s_avg}")
        
    # =================== Results ========================================
    print("\nResults:")
    print(f"Number of Stockers = {stocker_counts}")
    print(f"AVG Picker Wait    = {picker_wait_results}")
    print(f"AVG Stocker Wait   = {stocker_wait_results}")

    plotter.plot_experiment_1(stocker_counts, picker_wait_results, stocker_wait_results)


def experiment_2():
    """
    In this test we try to break the logic of our unfair trolley resource (we reserve 1 trolley always to stockers)
    It is divided in the part:

    1) We first try to get starvation on Pickers (because of a lot of stockers and few trolleys with no breaks)
    2) Then We add breaks to see if they can save our poor Pickers from stravation
    """
    base_settings = {
        'tick_ms': '1',
        'num_pickers': '1',
        'num_trolleys': '2',
        'stocker_break_duration': '0',
        'delivery_probability': '0.015',
    }
    update_properties(base_settings)

    stocker_counts = list(range(2, 21, 2)) 
    picker_throughput_no_breaks = []
    picker_throughput_with_breaks = []

    print("Starting Experiment 2:")

    # ================== Part 1 with no breaksS ========================
    print("\nPart 1, no breaks expected starvation of Pickers")
    print("fixed config: ", base_settings)
    print("variable config: {'num_stockers': '[2-20]'} 2..4..6..20")

    for s in stocker_counts:                            
        update_properties({'num_stockers': str(s)})
        _, _, p_cnt = run_simulation()
        picker_throughput_no_breaks.append(round(p_cnt, 2))
        print(f"Stockers: {s} | Total Picks completed: {p_cnt}")

    # ================= Part 2 with breaks ==============================
    base_settings['stocker_break_duration'] = '150'
    base_settings['stocker_break_interval_min'] = '10' 
    base_settings['stocker_break_interval_max'] = '20'
    update_properties(base_settings)

    print("\nPart 2, with breaks (hopefully no starvation for Pickers")
    print("fixed config: ", base_settings)
    print("variable config: {'num_stockers': '[2-20]'} 2..4..6..20")

    for s in stocker_counts:                            
        update_properties({'num_stockers': str(s)})
        _, _, p_cnt = run_simulation()
        picker_throughput_with_breaks.append(round(p_cnt, 2))
        print(f"Stockers: {s} | Total Picks completed: {p_cnt}")

    # =================== Results ========================================
    print("\nResults:")
    print(f"X_Stockers                      = {stocker_counts}")
    print(f"Y_picker_throughput (NO Breaks) = {picker_throughput_no_breaks}")
    print(f"Y_picker_throughput (BREAKS)    = {picker_throughput_with_breaks}\n")

    plotter.plot_experiment_2(stocker_counts, picker_throughput_no_breaks, picker_throughput_with_breaks)

def experiment_3():
    """
    This test is to understand which is the better value of trolleys and if it is related to the fomula k = [S + P] / 2.
    We keep fixed: 5 Pickers, 5 Stockers and we try a range of trolly values from 2 to 10.
    """
    base_settings = {
        'tick_ms': '1',
        'num_pickers': '5',
        'num_stockers': '5',
        'delivery_probability': '0.01',
        'initial_boxes_per_section': '0'            # this is because if there are already box in the section picker waiting time get corrputed by the first attempt
    }
    update_properties(base_settings)

    trolley_counts = list(range(2, 11))
    picker_throughput_results = []
    picker_wait_results = []
    stocker_wait_results = []

    print("Starting Experiment 3:")

    # =================== running simulations ========================================
    for t in trolley_counts:
        update_properties({'num_trolleys': str(t)})
        p_avg, s_avg, p_cnt = run_simulation()

        picker_throughput_results.append(p_cnt)
        picker_wait_results.append(round(p_avg, 2))
        stocker_wait_results.append(round(s_avg, 2))
        
        print(f"Trolleys: {t} | Picks: {p_cnt} | AVG Picker Wait: {p_avg} | AVG Stocker Wait: {s_avg}")

    # =================== Results ========================================
    print("\nResults Experiment 3:")
    print(f"X_Trolleys          = {trolley_counts}")
    print(f"Y_Picker_Throughput = {picker_throughput_results}")
    print(f"Y_Picker_Wait       = {picker_wait_results}")
    print(f"Y_Stocker_Wait      = {stocker_wait_results}\n")

    plotter.plot_experiment_3(trolley_counts, picker_throughput_results, picker_wait_results, stocker_wait_results)

def main():
    original_config = read_original_config()
    
    try:
        #experiment_1()
        #experiment_2()
        experiment_3()
    finally:
        restore_config(original_config)

if __name__ == '__main__':
    main()
