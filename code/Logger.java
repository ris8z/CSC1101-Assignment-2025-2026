/*
    authors:        Cathal Dwyer, Giuseppe Esposito;
    
    stN:            22391376, 22702205;
    
    date:           15/03/2026;
    
    description:   this is an help function to log an event with a variable number of key, value pairs 

    approach:      note that the function is synchronized beacuse println is threadsafe but the the process
                   of building the string it's not. (i.e. P1: we starting building a log at t = 150 but because the string is long
                   (i.e. let' take 2 process P1 and P2)
                        - P1 starts at tick = 150 on the CPU and has a big string to build
                        - So the CPU pause P1 and start P2
                        - P2 start at tick = 151 but has a small string so it get's instaltly printed
                        - Then the CPU finish P1 and print it after P2
                    We have in the logs first tick 151 then 150, to avoid these we just put synchronized and forget about it.
                    
*/
public class Logger {
    public static synchronized void log(String event, Object... pairs) {
        StringBuilder sb = new StringBuilder();
        sb.append("tick=").append(Clock.getTick());
        sb.append(" tid=").append(Thread.currentThread().getName());
        sb.append(" event=").append(event);

        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Logger.log() requires even number of key/value pairs");
        }

        for (int i = 0; i < pairs.length; i += 2) {
            sb.append(' ').append(pairs[i]).append('=').append(pairs[i + 1]);
        }

        System.out.println(sb.toString());
    }
}
