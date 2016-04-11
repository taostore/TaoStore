package TaoProxy;

public interface Subtree {
    /**
     * @brief Method to add a path to the subtree
     * @param path
     */
    void addPath(Path path);

    /**
     * @brief Method to get requested path from subtree
     * @param pathID
     * @return path with ID == pathID
     */
    Path getPath(long pathID);
}
