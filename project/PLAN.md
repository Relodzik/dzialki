# Minecraft Plot Plugin Development Plan

## Overview
This plugin will allow players to create protected plots on a Minecraft server using a special "Plot Heart" item. The plots will protect an area of 17x17 blocks (8 blocks in each direction from the center) from floor to ceiling.

## Core Features
1. **Plot Creation and Management**
   - Create plots using a special "Plot Heart" item (red shulker box)
   - Only the plot owner can destroy the Plot Heart
   - Random 4-character TAG generation for each plot
   - Entry/exit messages when players enter/leave plots

2. **Protection System**
   - Block non-members from building/breaking blocks within plots
   - Block piston actions from outside plots affecting blocks inside
   - Block dispensers from outside plots affecting the inside

3. **Commands**
   - `/dzialka daj` - Give a Plot Heart to a player (admin only)
   - `/dzialka usun TAG` - Delete a plot (admin only)
   - `/dzialka dom` - Teleport to your plot's home location
   - `/dzialka tp TAG` - Teleport to a plot (admin only)
   - `/dzialka ustaw` - Set the teleport location for your plot
   - `/dzialka zapros` - Invite a player to your plot
   - `/dzialka dolacz TAG` - Join a plot after being invited

4. **Data Persistence**
   - Save plot data, members, and owners to persist across server restarts

## Implementation Steps

### 1. Project Setup
- Set up Maven project with Paper API 1.21.1
- Create main plugin class and plugin.yml

### 2. Data Models
- Create Plot class to store plot data (location, owner, members, tag)
- Create PlotManager to handle plot operations and storage

### 3. Command Implementation
- Create command handler for all `/dzialka` commands
- Implement permission checks

### 4. Plot Creation and Destruction
- Implement Plot Heart item creation
- Handle placing and breaking of Plot Heart
- Generate random TAG for plots

### 5. Protection System
- Implement event listeners for block breaking/placing
- Handle piston and dispenser protection
- Implement entry/exit detection and messages

### 6. Plot Membership
- Implement invitation system
- Handle plot membership management

### 7. Teleportation
- Implement teleportation to plots
- Allow setting custom teleport locations

### 8. Data Persistence
- Implement data saving and loading
- Ensure data persists across server restarts

### 9. Testing and Refinement
- Test all features
- Fix bugs and optimize performance