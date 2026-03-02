public class Logger {

    public static void log(String event, Object... pairs) {
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
