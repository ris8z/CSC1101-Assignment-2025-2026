---
id: 2026-03-17_CSC1101-Assignment-Report
aliases: []
tags:
  - CSC1101
date: 2026-03-17-Tue
---

# CSC1101 Assignment Report

**Warehouse java simulation project**
Cathal Dwyer (22391376)    Giuseppe Esposito(22702205)
[google drive video]()        (5 min max)

## List of working functionality

| Description                                                                                                                                 | Status  | Note |
| ------------------------------------------------------------------------------------------------------------------------------------------- | ------- | ---- |
| A delivery of 10 boxes is made with a probability of .01 every tick                                                                         | Working | n/a  |
| Capacity is unlimited within each section                                                                                                   | Working | n/a  |
| 1 stocker (threaded) / multiple pickers (threaded)                                                                                          | Working | n/a  |
| 5 sections e.g., electronics, medicines, books, clothes, and tools.                                                                         | Working | n/a  |
| Real-time reporting (as above)                                                                                                              | Working | n/a  |
| A variable defined in your code (that I can adjust) that determines how long in milliseconds a tick is (e.g. 100 ms)                        | Working | n/a  |
| More than 1 stocker                                                                                                                         | Working | n/a  |
| Prioritising stocking boxes in certain section based on the number of pickers waiting/empty sections (to minimise picker wait time)         | Working | n/a  |
| Sections with a limited capacity e.g., each section may only hold 10 boxes                                                                  | Working | n/a  |
| Configurable parameters (e.g., number of stockers, number of sections, control of probabilities of picker behaviour, etc.) via config file. | Working | n/a  |
| Breaks taken by stockers (e.g., every 200-300 ticks a stocker will take a break of 150 ticks).                                              | Working | n/a  |
| Pickers and stockers must acquire a trolley before they can begin their task                                                                | Working | n/a  |
| Stocker acquires trolley before staging and holds it until it is empty                                                                      | Working | n/a  |
| Picker acquires trolley before section pick and holds it until box is removed                                                               | Working | n/a  |
| A trolley may only be released when it is empty                                                                                             | Working | n/a  |
| Trolleys store at most 10 boxes and are shared between pickers and stockers                                                                 | Working | n/a  |
| Total number of trolleys K is an adjustable parameter (default: floor((S+P)/2))                                                             | Working | n/a  |
| Trolleys are immediately available to actors once released                                                                                  | Working | n/a  |
## Division of work

Cathal Dwyer worked on concurrent actors behaviors: `Section.java`, `StockerThread.java`, and `PickerThread.java`, ensuring mutual exclusion, the logic of the movement and wait/notify cycles of the worker. 

Giuseppe Esposito worked on the shared resource `TrolleyPool.java`, `StagingArea.java`, `DeliveryThread.java`, and the config system `WarehouseConfig.java`. 

We both worked on the report and video.
Note that the files are not strict boundaries we both worked on basically all files because logic often overlaps.

## Running the code (on Linux/Ubuntu)
How to compile and run the code:
```bash
javac *.java
java Warehouse
```

There is a config file called `warehouse.properties` where we can play with a lot of the simulation parameters:
```python
# ====== GENERAL ======================
# time + seed
tick_ms=100
ticks_per_day=1000
random_seed=42


# ====== RESOURCES =====================
# section
section_names=electronics,books,medicines,clothes,tools
initial_boxes_per_section=5
section_capacity=10

# trolleys (min value = 2)
num_trolleys=5


# ====== CONURRENT ACTORS ==============
# delivery 
delivery_probability=0.01
boxes_per_delivery=10

# stockers num + movment and job timing
num_stockers=5
travel_base_ticks=10
travel_ticks_per_box=1
stock_ticks_per_box=1
staging_take_ticks=1

# stockers breaks
stocker_break_interval_min=200
stocker_break_interval_max=300
stocker_break_duration=150

# pickers
num_pickers=2
target_picks_per_day=100
pick_ticks=1
```

## The considered tasks/dependencies in your program
![[Pasted image 20260317213650.png]]
**Active tasks**
1. Delivery thread: it handles the delivery event (bringing boxes to the staging area)
2. Stockers Thread: it gets the boxes from the staging area and move them to the sections.
3. Pickers Thread: it pick a box from a section to (idk probably sell it)

**Resource dependencies**
1. Stockers and Pickers depends on Trolleys Pool (because they need it to work)
2. Stockers depends on Delivery Task (if no boxes in the staging area stocker waits)
3. Pickers depends on Stockers refilling section (if there are 0 boxes in a section a picker waits)
4. Pickers depends on Stocker working state (if a stocker is stocking a section picker waits)
5. Stockers depends on Pickers to empty sections (if a stokers try to stock in a full section it needs to skip it)j

## Anaylsis of the System 
The define the strcure of our system we started form analising the resource and according to possible race condition or problems, we define both them
and the behviour of the actors that interacts with them.

### Staging area: 
This is a resource used by Stocker threads and Delivery threads.
**Requirements**
1. only 1 stocker can interact with this area at time.
2. But nothing is said about stocker + delivery interaction with this area (so we will assume that can be done at same time)

**Approach**
the problem is like a **producer consumer patter** where the delivery thread produces boxes and the stocker consume them, to make both actors work at the same time we use a `LinkedBlockingQueue`. Then we use 2 locks:
1. stocker lock (ensure that only 1 stocker is interacting with the area at time)
2. delivery lock plus a condition (deliveryWatingRoom) allow us to put stocker threads at sleep while there is no delivery.

**Fairness**
The stocker lock is implemented with `ReentrantLock(true)`  that ensure a FIFO (First in First out) wakes up of threads, to avoid starvation (i.e. some stocker never picking a box)
While the delivery lock is not fair because it's used to put a sleep stockers while there is no boxes, once there are boxes the fairiness is granted by the other lock. (it is just a ring bell)

**Dead lock / making progress**
Here we should be fine because the actors are not related in a ciruclar way:
Stocker depends on delivery by number (2), while the delivery is indpendent.


### Section area
This is a resource used by Stocker threads and picker threads.
**Requirements**
- if a stocker is working no other actor is allowed (no stocker no picker)
- a stocker will either wait or move if the section is full
- more picker can work at the same time
- picker will wait for boxes if the section is empty 

**Approach**
the problem is similar to the Staging area, now the producer is the Stocker and the consumer is the Picker, main differences are:
- producing takes time and locks the Consumer out, 
- more consumer can cansume at the same time
- boxes can be rappresent by their number as a simple `int` (because 1 section stores only 1 type)

We use 1 lock to protect the `boxes count` with 2 conditions:
1. stockerWaitingRoom (stockers waits only if another stocker is wokring)
2. pickerWaitingRoom (pickers waits if there are no boxes or a stocker is working)

Note we flag that a stocker is working with another variable `stockerActive`.

**Fairness**
The lock used is `ReentrantLock(true)` to ensure FIFO behaviour and avoid starvation.

**Dead lock / making progress**
Let's assume infinite trollyes to anylis this. (after we talk about scenario with finte number of trolleys)
There is risk of dead lock, because Picker depends on stocker by number (3 rifill section, 4 working state)
and Stocker denpends on picker by number (5 empty the section).
To mitigate this problem we designed the behvuoir of the stockers in such a way that there is *No hold and wait for stockers*, 
It starts from section area pick boxes, iterates through all sections (order by number of wating pickers and number of boxes)
go back to the section area and start over.

### Trolleys pool
Oh no!
 
## Appendix

