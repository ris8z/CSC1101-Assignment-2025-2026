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

### Resource
The define the strcure of our system we started form analising the resource and according to possible race condition or problems, we define both them
and the behviour of the actors that interacts with them.

#### Staging area: 
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


#### Section area
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
Let's assume infinite trollyes (or no trolley at all) to anylis this. (after we talk about scenario with finte number of trolleys)
There is risk of dead lock, because Picker depends on stocker by number (3 rifill section, 4 working state)
and Stocker denpends on picker by number (5 empty the section).

To mitigate this problem we designed the behvuoir of the stockers in such a way that there is *No hold and wait for stockers*, 
It starts from section area pick boxes, iterates through all sections (order by number of wating pickers and number of boxes)
go back to the section area and start over.

#### Trolleys pool
This is a resource used by Stocker threads and picker threads.

**Requirements**
- both pickers and stocker must acquire a trolley before any actions
- pickers must hold the trolly until the can remove a box
- stocker must hold the trolly unitl it gets empty
- trolleys can stores at most 10 boxes
- by defult number of trolle k is `K = floor((num_stockers + num_pickers)/2)`
- trolleys does not need to move from the pool to the actor they telport instatly
- we need to log the event of get and release a trolley (USING THE TROLLEY ID)

**Approach**
Allora il problema qua e' che dobbiamo contoare quanti carrelli sono disponibili tra vari actors che lo richiedono (picker & stockers)
e' quindi una *semaphore pattern*, il problema e' che non possiamo semplicemente contare quanti carrelli rimangono con un intero per via dell
del ultimo requirment ovvero dobbiamo printare l'id del carello preso, per questo abbiamo deciso di creare una linkedlist e metterici dentro
tanti nodi quanto il numero di carelli e l'unico valore e' un intero che rappresenta l'id del carello che va da $[1,K]$

**Fairness**
The lock used is `ReentrantLock(true)` to ensure FIFO behaviour and MITGATE starvation.
because as we will see in the next session in our desing it is possible to encounter that.

**Dead lock / making progress**
So as we said before we have a circular dependecy in section:
- pickers depdens on stocker (for refilling the sections and their woring state)
- sotker depends on picker (for consumeing boxes and therefore craeting space to place boxes in sections)

Without trollye we mitage the problem by deleting making the stocker not wait for the section to empty but going through
all section the go back to staging check if they can pick others boxes and try again.
in this way the section does not get blocked permantely by stockers and the pickers can make space for the stocekrs.

When we add trolley to the equations the problem gets more complicated because if all the trolley are taken by pickers
that are waiting in empty rooms they will never recives any packages from stockers because they have no access to trolleys.

To solve this we though so pickers can pick thaks to the delation of the waiting of sotkcers, but we need to ensure that
if a pickers is wating at least 1 stocker will try to help me, so we deide to make the trolley resousce unfair, essentialy
we reserve always 1 trolley to the stokers (in fact stocker don't wait if there is only 1 cart in the pool while picker have to!)

This only mitigate the problem in fact with only 1 trolley the deadlock is certian becuase pickers can never get that trolley,
we try to think about a special case for handling 1 trolley but in all case we found that if the trolley is in pickers hand and there is 
an empty section the simulation get's blocked forever, so the base number of trolley is at least 2.

As you can imagine this unfairness in the trolley pool can lead to deadlock even in the other sense if there are to many stockers
and to many delivery and the sections are all full, we will just have a bunch of full trolley in stockers hand going around the
place without ever empty their trolly becausep pickers can't work without a trolley, but this some time get's mitegated buy stockers brakes,
obviuslly not when we are already in this situation breaks are uselless because the trolly will remain there any way, but it mitigate a bit
the process to get to that final disrupctive situaation.

### Actors/Threads lifecylce

#### Delivery Thread lifecycle
every tick it rolles a dice and check if the possiblity of a delivery event is met, if it is:
- it generate the boxes for the delivery
- add the boxes to the stagin area by using the stagin area object (this singal all stocker of a delivery event)
- increment the delivery count and log the delivery

#### Stocker Thread lifecycle
Every cycle it:
- if it needs to take a break, if it is the case the thread sleep for that duration and when wake up it schedule the time of the next break
- check if it owns a trolly from the previous cycle, if it don't i'll wait until aquiring a new empty trolley and wait until i'll get someboxes from the stagin area, if does own a trolly it continues the cyle with that trolly (proprbaly in the last cylce all the section were full and some boxes is still in the trolley)
- it starts at the stagin area, evaluate the beast section to go (by sorting them by the number of pickers waiting, and if equal by the number of boxes)
- then travel to each section try to stock the section (skip it if it is full)
- then goes back to the staging area
- if the trolley is empty it release it otherwise not

#### Picker Thread lifecycle
Every cycle it:
- it waits a dynamic cooldown to try to get the right number of pick event per day (if it is early it waits the MEAN_SLEEP_TICKS othersiwe it skips the waiting)
- then wait to acquire a trolley, and log it
- select a random section,
- generate the pick id (by incrementing an int atomic variable)
- log the pick_start action
- starts the action of picking the boxes
- after it got the ownership of the boxes it waits outside the lock (PICK_TICS ~= 1)
- then log the pick_done action
- finally it release the trolley

## Appendix

