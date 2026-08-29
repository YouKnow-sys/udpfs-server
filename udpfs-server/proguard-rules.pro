# The gomobile AAR ships -keep rules for go.** and com.udpfs.** as consumer
# rules, so AGP applies them automatically; only the warning suppression is
# needed here (consumer rules do not include it).
-dontwarn go.**
