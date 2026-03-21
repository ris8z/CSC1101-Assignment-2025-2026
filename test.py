import subprocess
import re

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

def run_simulation_and_parse():
    out = ""
    try:
        result = subprocess.run(['java', '-cp', 'code', 'Warehouse'], capture_output=True, text=True, timeout=3)
        out = result.stdout
    except subprocess.TimeoutExpired as e:
        out = e.stdout if e.stdout else ""
        if isinstance(out, bytes):
            out = out.decode('utf-8', errors='ignore')

    if not out:
        print("No char was captured! Java probaly exploded")
        return 0, 0

    picker_ticks = re.findall(r'event=pick_done.*waited_ticks=(\d+)', out)
    stocker_ticks = re.findall(r'event=stocker_load.*waited_ticks=(\d+)', out)
    
    p_avg = sum(int(t) for t in picker_ticks) / len(picker_ticks) if picker_ticks else 0
    s_avg = sum(int(t) for t in stocker_ticks) / len(stocker_ticks) if stocker_ticks else 0
    
    return p_avg, s_avg

def experiment_1_tradeoff():
    """ This experiemnt is too try to prove that we can't increase performance infiitly just by adding more threads
        We keep picker and trolleys fixed, and we change the number of stocker between 1 to 15:
        creating 15 simulation that we run just for 3 second.
    """
    base_settings = {
        'tick_ms': '1',
        'num_pickers': '5',
        'num_trolleys': '20'
    }
    update_properties(base_settings)

    print("-----------------------------------------------------")
    print("Starting Experiment 1: fixed Picker, Stocker between [1-16]")
    print("We start with 1 stocker up to 15 to see how the wating times changes")
    print("config: ", base_settings)
    print("-----------------------------------------------------")
    
    stocker_counts = list(range(1, 16))
    picker_wait_results = []
    stocker_wait_results = []
    
    for s in stocker_counts:
        update_properties({'num_stockers': str(s)})
        p_avg, s_avg = run_simulation_and_parse()
        
        picker_wait_results.append(round(p_avg, 2))
        stocker_wait_results.append(round(s_avg, 2))
        
        print(f"Stockers: {s} | AVG Picker Wait: {p_avg} | AVG Stocker Wait: {s_avg}")
        
    print("\nResults:")
    print(f"Number of Stockers = {stocker_counts}")
    print(f"AVG Picker Wait    = {picker_wait_results}")
    print(f"AVG Stocker Wait   = {stocker_wait_results}")

def main():
    original_config = read_original_config()
    
    try:
        experiment_1_tradeoff()
    finally:
        restore_config(original_config)
        print("\n End of the testing")

if __name__ == '__main__':
    main()
