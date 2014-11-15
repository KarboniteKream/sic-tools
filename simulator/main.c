#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <time.h>

#include <curses.h>

#define REG_A 0
#define REG_X 1
#define REG_L 2
#define REG_B 3
#define REG_S 4
#define REG_T 5

#define MAX_ADDR 1048575

#define DEV_STDIN 0
#define DEV_STDOUT 1
#define DEV_STDERR 2
#define DEV_FILE 3

#define ERR_OPCODE 0
#define ERR_ADDR 1
#define ERR_IMPL 2

WINDOW *cpu;
char *programName;
bool running;
bool reset;

typedef union {
    double f;
    uint64_t i;
} flt;

const char *OPCODES[] = {
    "LDA",
    "LDX",
    "LDL",
    "STA",
    "STX",
    "STL",
    "ADD",
    "SUB",
    "MUL",
    "DIV",
    "COMP",
    "TIX",
    "JEQ",
    "JGT",
    "JLT",
    "J",
    "AND",
    "OR",
    "JSUB",
    "RSUB",
    "LDCH",
    "STCH",
    "ADDF",
    "SUBF",
    "MULF",
    "DIVF",
    "LDB",
    "LDS",
    "LDF",
    "LDT",
    "STB",
    "STS",
    "STF",
    "STT",
    "COMPF",
    "NOP",
    "ADDR",
    "SUBR",
    "MULR",
    "DIVR",
    "COMPR",
    "SHIFTL",
    "SHIFTR",
    "RMO",
    "SVC",
    "CLEAR",
    "TIXR",
    "NOP",
    "FLOAT",
    "FIX",
    "NORM",
    "NOP",
    "LPS",
    "STI",
    "RD",
    "WD",
    "TD",
    "NOP",
    "STSW",
    "SSK",
    "SIO",
    "HIO",
    "TIO",
    "NOP"
};

typedef enum {
    ADD = 0x18,
    ADDF = 0x58,
    ADDR = 0x90,
    AND = 0x40,
    CLEAR = 0xB4,
    COMP = 0x28,
    COMPF = 0x88,
    COMPR = 0xA0,
    DIV = 0x24,
    DIVF = 0x64,
    DIVR = 0x9C,
    FIX = 0xC4,
    FLOAT = 0xC0,
    HIO = 0xF4,
    J = 0x3C,
    JEQ = 0x30,
    JGT = 0x34,
    JLT = 0x38,
    JSUB = 0x48,
    LDA = 0x00,
    LDB = 0x68,
    LDCH = 0x50,
    LDF = 0x70,
    LDL = 0x08,
    LDS = 0x6C,
    LDT = 0x74,
    LDX = 0x04,
    LPS = 0xD0,
    MUL = 0x20,
    MULF = 0x60,
    MULR = 0x98,
    NORM = 0xC8,
    OR = 0x44,
    RD = 0xD8,
    RMO = 0xAC,
    RSUB = 0x4C,
    SHIFTL = 0xA4,
    SHIFTR = 0xA8,
    SIO = 0xF0,
    SSK = 0xEC,
    STA = 0x0C,
    STB = 0x78,
    STCH = 0x54,
    STF = 0x80,
    STI = 0xD4,
    STL = 0x14,
    STS = 0x7C,
    STSW = 0xE8,
    STT = 0x84,
    STX = 0x10,
    SUB = 0x1C,
    SUBF = 0x5C,
    SUBR = 0x94,
    SVC = 0xB0,
    TD = 0xE0,
    TIO = 0xF8,
    TIX = 0x2C,
    TIXR = 0xB8,
    WD = 0xDC
} OPCODE;

struct {
    int32_t reg[6];
    flt f;
    uint32_t pc;
    uint32_t sw;

    OPCODE opcode;

    uint8_t mem[MAX_ADDR + 1];
    FILE *devices[256];
} MACHINE;

void printError(uint8_t type, uint32_t code) {
    if (type == ERR_OPCODE) {
        char opcode[2];
        snprintf(opcode, 2, "%02X", code);
        printf("Invalid opcode: %s\n", opcode);
    } else if (type == ERR_ADDR) {
        char address[8];
        snprintf(address, 8, "0x%06X", code);
        printf("Invalid address: %s\n", address);
    }
}

void storeByte(uint8_t reg, uint32_t addr) {
    if (addr > MAX_ADDR) {
        printError(ERR_ADDR, addr);
        return;
    }

    MACHINE.mem[addr] = MACHINE.reg[reg] & 0x000000FF;
}

int32_t loadWord(uint32_t addr) {
    int32_t num = 0;

    num |= (MACHINE.mem[addr++] << 16);
    num |= (MACHINE.mem[addr++] << 8);
    num |= MACHINE.mem[addr];

    if ((num & 0x00800000) > 0) {
        num |= 0xFF000000;
    }

    return num;
}

void storeWord(uint8_t reg, uint32_t addr) {
    MACHINE.mem[addr++] = (MACHINE.reg[reg] & 0xFF0000) >> 16;
    MACHINE.mem[addr++] = (MACHINE.reg[reg] & 0x00FF00) >> 8;
    MACHINE.mem[addr] = (MACHINE.reg[reg] & 0x0000FF);
}

flt loadFloat(uint32_t addr, uint8_t nmode) {
    flt f;

    if ((nmode & 0x01) > 0) {
        f.f = (double)addr;
    } else {
        uint64_t num = 0;
        num |= (uint64_t)MACHINE.mem[addr++] << 40;
        num |= (uint64_t)MACHINE.mem[addr++] << 32;
        num |= MACHINE.mem[addr++] << 24;
        num |= MACHINE.mem[addr++] << 16;
        num |= MACHINE.mem[addr++] << 8;
        num |= MACHINE.mem[addr];

        f.i = num << 16;
    }

    return f;
}

void storeFloat(uint32_t addr) {
    MACHINE.mem[addr++] = (MACHINE.f.i >> 56) & 0xFF;
    MACHINE.mem[addr++] = (MACHINE.f.i >> 48) & 0xFF;
    MACHINE.mem[addr++] = (MACHINE.f.i >> 40) & 0xFF;
    MACHINE.mem[addr++] = (MACHINE.f.i >> 32) & 0xFF;
    MACHINE.mem[addr++] = (MACHINE.f.i >> 24) & 0xFF;
    MACHINE.mem[addr] = (MACHINE.f.i >> 16) & 0xFF;
}

void setDevice(uint8_t id, uint8_t type, const char *filename) {
    if (type == DEV_STDIN) {
        MACHINE.devices[id] = stdin;
    } else if (type == DEV_STDOUT) {
        MACHINE.devices[id] = fopen("stdout.txt", "a+b");
    } else if (type == DEV_STDERR) {
        MACHINE.devices[id] = stderr;
    } else if (type == DEV_FILE && filename != NULL) {
        if (MACHINE.devices[id] == NULL) {
            MACHINE.devices[id] = fopen(filename, "a+b");
        } else {
            fclose(MACHINE.devices[id]);
            MACHINE.devices[id] = fopen(filename, "a+b");
        }
    } else {
        MACHINE.devices[id] = NULL;
    }
}

bool testDevice(uint8_t id) {
    return (MACHINE.devices[id] != NULL);
}

uint8_t readDevice(uint8_t id) {
    uint8_t byte = 0;

    if (MACHINE.devices[id] == NULL) {
        char filename[7];
        snprintf(filename, 7, "%02X.dev", id);
        MACHINE.devices[id] = fopen(filename, "a+b");
    }

    fread(&byte, sizeof(uint8_t), 1, MACHINE.devices[id]);

    return byte;
}

void writeDevice(uint8_t id, uint8_t byte) {
    if (MACHINE.devices[id] == NULL) {
        char filename[7];
        snprintf(filename, 7, "%02X.dev", id);
        MACHINE.devices[id] = fopen(filename, "a+b");
    }

    fwrite(&byte, sizeof(uint8_t), 1, MACHINE.devices[id]);
    fflush(MACHINE.devices[id]);
}

void initMachine() {
    memset(MACHINE.reg, 0, sizeof(MACHINE.reg));
    MACHINE.f.i = 0;
    MACHINE.pc = 0;
    MACHINE.sw = 0;

    memset(MACHINE.mem, 0, sizeof(MACHINE.mem));
    memset(MACHINE.devices, 0, sizeof(MACHINE.devices));

    setDevice(0, DEV_STDIN, NULL);
    setDevice(1, DEV_STDOUT, NULL);
    setDevice(2, DEV_STDERR, NULL);

    for (int i = 3; i < 256; i++) {
        setDevice(i, DEV_FILE, NULL);
    }
}

// TODO: Separate to LOAD/STORE.
int32_t getAddress(int32_t addr, uint8_t mode, bool immediate) {
    if (immediate == true) {
        if (mode == 0x01) {
            return addr;
        } else if (mode == 0x02) {
            return loadWord(loadWord(addr));
        } else {
            return loadWord(addr);
        }
    } else {
        return ((mode == 0x02) ? loadWord(addr) : addr);
    }
}

bool load(const char *filename) {
    FILE *file = fopen(filename, "r");

    if (file == NULL) {
        return false;
    }

    char line[75];
    bool eof = false;

    while (eof == false) {
        fgets(line, 75, file);
        line[strlen(line) - 1] = '\0';

        switch (line[0]) {
            case 'T':
                {
                    uint32_t addr = (line[1] - (line[1] < 58 ? 48 : 55)) << 20;
                    addr |= (line[2] - (line[2] < 58 ? 48 : 55)) << 16;
                    addr |= (line[3] - (line[3] < 58 ? 48 : 55)) << 12;
                    addr |= (line[4] - (line[4] < 58 ? 48 : 55)) << 8;
                    addr |= (line[5] - (line[5] < 58 ? 48 : 55)) << 4;
                    addr |= line[6] - (line[6] < 58 ? 48 : 55);

                    char *data = &line[9];

                    for (unsigned int i = 0; i <= strlen(data) - 1; i += 2) {
                        uint8_t byte = (data[i] - (data[i] < 58 ? 48 : 55)) << 4;
                        byte |= data[i + 1] - (data[i + 1] < 58 ? 48 : 55);
                        MACHINE.mem[addr++] = byte;
                    }
                }
                break;

            case 'E':
                MACHINE.pc = strtoul(&line[1], NULL, 16);
                eof = true;
                break;

            case 'H':
            case 'D':
            case 'R':
            case 'M':
                break;
        }
    }

    fclose(file);
    return true;
}

bool execF1() {
    bool status = true;

    switch (MACHINE.opcode) {
        case FIX:
            MACHINE.reg[REG_A] = (int32_t) MACHINE.f.f;
            break;

        case FLOAT:
            MACHINE.f.f = MACHINE.reg[REG_A];
            break;

        case HIO:
        case NORM:
        case SIO:
        case TIO:
            break;

        default:
            status = false;
            break;
    }

    return status;
}

bool execF2(uint8_t byte1) {
    bool status = true;

    switch (MACHINE.opcode) {
        case ADDR:
            MACHINE.reg[byte1 & 0x0F] += MACHINE.reg[byte1 >> 4];
            break;

        case CLEAR:
            MACHINE.reg[byte1 >> 4] = 0;
            break;

        case COMPR:
            if (MACHINE.reg[byte1 >> 4] == MACHINE.reg[byte1 & 0x0F]) {
                MACHINE.sw &= 0xFFFF3F;
                MACHINE.sw |= 0x000040;
            } else if (MACHINE.reg[byte1 >> 4] > MACHINE.reg[byte1 & 0x0F]) {
                MACHINE.sw &= 0xFFFF3F;
                MACHINE.sw |= 0x000080;
            } else if (MACHINE.reg[byte1 >> 4] < MACHINE.reg[byte1 & 0x0F]) {
                MACHINE.sw &= 0xFFFF3F;
            }
            break;

        case DIVR:
            MACHINE.reg[byte1 & 0x0F] /= MACHINE.reg[byte1 >> 4];
            break;

        case MULR:
            MACHINE.reg[byte1 & 0x0F] *= MACHINE.reg[byte1 >> 4];
            break;

        case RMO:
            MACHINE.reg[byte1 & 0x0F] = MACHINE.reg[byte1 >> 4];
            break;

        case SHIFTL:
            {
                uint8_t reg = byte1 >> 4;
                uint8_t num = (byte1 & 0x0F) + 1;

                MACHINE.reg[reg] = (MACHINE.reg[reg] << num) | (MACHINE.reg[reg] & 0x00FFFFFF) >> (24 - num);

                if ((MACHINE.reg[reg] & 0x00800000) > 0) {
                    MACHINE.reg[reg] |= 0xFF000000;
                } else {
                    MACHINE.reg[reg] &= 0x00FFFFFF;
                }
            }
            break;

        case SHIFTR:
            MACHINE.reg[byte1 >> 4] >>= (byte1 & 0x0F) + 1;
            break;

        case SUBR:
            MACHINE.reg[byte1 & 0x0F] -= MACHINE.reg[byte1 >> 4];
            break;

        case SVC:
            break;

        case TIXR:
            MACHINE.reg[REG_X]++;

            if (MACHINE.reg[REG_X] == MACHINE.reg[byte1 >> 4]) {
                MACHINE.sw &= 0xFFFF3F;
                MACHINE.sw |= 0x000040;
            } else if (MACHINE.reg[REG_X] > MACHINE.reg[byte1 >> 4]) {
                MACHINE.sw &= 0xFFFF3F;
                MACHINE.sw |= 0x000080;
            } else if (MACHINE.reg[REG_X] < MACHINE.reg[byte1 >> 4]) {
                MACHINE.sw &= 0xFFFF3F;
            }
            break;

        default:
            status = false;
            break;
    }

    if (status == true) {
        mvwprintw(cpu, 2, 1, "%d %d", byte1 >> 4, byte1 & 0x0F);

        mvwprintw(cpu, 1, 15, "%02X%02X    ", MACHINE.opcode, byte1);
        mvwprintw(cpu, 2, 15, "%02X  (%6s)", MACHINE.opcode, OPCODES[MACHINE.opcode >> 2]);
        mvwprintw(cpu, 3, 15, "__  ____");
        mvwprintw(cpu, 4, 15, "00000 -> 00000");
    }

    return status;
}

bool exec(uint8_t byte1) {
    uint8_t nmode = MACHINE.opcode & 0x03;
    MACHINE.opcode = MACHINE.opcode & 0xFC;
    uint8_t amode = (byte1 & 0xF0) >> 4;
    int32_t caddr, addr;

    uint8_t byte2 = MACHINE.mem[MACHINE.pc++], byte3 = 0;

    if (nmode == 0) {
        caddr = (byte1 & 0x7F) << 8;
        caddr |= byte2;

        if ((caddr & 0x00004000) > 0) {
            caddr |= 0xFFFF8000;
        }

        addr = caddr;
        amode = amode & 0x08;

        if (amode > 0) {
            addr += MACHINE.reg[REG_X];
        }
    } else {
        caddr = (byte1 & 0x0F) << 8;
        caddr |= byte2;

        if ((caddr & 0x00000800) > 0) {
            caddr |= 0xFFFFF000;
        }

        if ((amode & 0x01) > 0) {
            byte3 = MACHINE.mem[MACHINE.pc++];
            caddr = caddr << 8;
            caddr |= byte3;
        }

        if ((amode & 0x02) > 0) {
            addr = MACHINE.pc + caddr;
        } else if ((amode & 0x04) > 0) {
            addr = MACHINE.reg[REG_B] + caddr;
        } else {
            addr = caddr;
        }

        if ((amode & 0x08) > 0) {
            addr += MACHINE.reg[REG_X];
        }
    }

    mvwprintw(cpu, 1, 15, "%02X%02X%02X  ", MACHINE.opcode | nmode, byte1, byte2);
    if ((amode & 0x01) > 0) {
        mvwprintw(cpu, 1, 21, "%02X", byte3);
    }
    mvwprintw(cpu, 2, 15, "%02X  (%6s)", MACHINE.opcode, OPCODES[MACHINE.opcode >> 2]);
    mvwprintw(cpu, 3, 15, "%c%c", (nmode & 0x02) > 0 ? 'N' : '_', (nmode & 0x01) > 0 ? 'I' : '_');
    mvwprintw(cpu, 3, 20, "%c%c%c%c", (amode & 0x08) > 0 ? 'X' : '_', (amode & 0x04) > 0 ? 'B' : '_', (amode & 0x02) > 0 ? 'P' : '_', (amode & 0x01) > 0 ? 'E' : '_');
    mvwprintw(cpu, 4, 15, "%05X -> %05X", caddr & 0xFFFFF, addr & 0xFFFFF);

    switch (MACHINE.opcode) {
        case ADD:
            MACHINE.reg[REG_A] += getAddress(addr, nmode, true);
            break;

        case ADDF:
            {
                flt f = loadFloat(addr, nmode);
                MACHINE.f.f += f.f;
            }
            break;

        case AND:
            MACHINE.reg[REG_A] &= getAddress(addr, nmode, true);
            break;

        case COMP:
            {
                int32_t num = getAddress(addr, nmode, true);

                if (MACHINE.reg[REG_A] == num) {
                    MACHINE.sw &= 0xFFFF3F;
                    MACHINE.sw |= 0x000040;
                } else if (MACHINE.reg[REG_A] > num) {
                    MACHINE.sw &= 0xFFFF3F;
                    MACHINE.sw |= 0x000080;
                } else if (MACHINE.reg[REG_A] < num) {
                    MACHINE.sw &= 0xFFFF3F;
                }
            }
            break;

        case COMPF:
            {
                flt num = loadFloat(addr, nmode);

                if (MACHINE.f.f == num.f) {
                    MACHINE.sw &= 0xFFFF3F;
                    MACHINE.sw |= 0x000040;
                } else if (MACHINE.f.f > num.f) {
                    MACHINE.sw &= 0xFFFF3F;
                    MACHINE.sw |= 0x000080;
                } else if (MACHINE.f.f < num.f) {
                    MACHINE.sw &= 0xFFFF3F;
                }
            }
            break;

        case DIV:
            MACHINE.reg[REG_A] /= getAddress(addr, nmode, true);
            break;

        case DIVF:
            {
                flt f;
                f = loadFloat(addr, nmode);
                MACHINE.f.f /= f.f;
            }
            break;

        case J:
            MACHINE.pc = getAddress(addr, nmode, false);
            break;

        case JEQ:
            if ((MACHINE.sw & 0xC0) == 0x40) {
                MACHINE.pc = getAddress(addr, nmode, false);
            }
            break;

        case JGT:
            if ((MACHINE.sw & 0xC0) == 0x80) {
                MACHINE.pc = getAddress(addr, nmode, false);
            }
            break;

        case JLT:
            if ((MACHINE.sw & 0xC0) == 0x00) {
                MACHINE.pc = getAddress(addr, nmode, false);
            }
            break;

        case JSUB:
            MACHINE.reg[REG_L] = MACHINE.pc;
            MACHINE.pc = getAddress(addr, nmode, false);
            break;

        case LDA:
            MACHINE.reg[REG_A] = getAddress(addr, nmode, true);
            break;

        case LDB:
            MACHINE.reg[REG_B] = getAddress(addr, nmode, true);
            break;

        case LDCH:
            MACHINE.reg[REG_A] &= 0xFFFF00;
            MACHINE.reg[REG_A] |= (getAddress(addr, nmode, true) & 0xFF0000) >> 16;
            break;

        case LDF:
            MACHINE.f = loadFloat(addr, nmode);
            break;

        case LDL:
            MACHINE.reg[REG_L] = getAddress(addr, nmode, true);
            break;

        case LDS:
            MACHINE.reg[REG_S] = getAddress(addr, nmode, true);
            break;

        case LDT:
            MACHINE.reg[REG_T] = getAddress(addr, nmode, true);
            break;

        case LDX:
            MACHINE.reg[REG_X] = getAddress(addr, nmode, true);
            break;

        case LPS:
            break;

        case MUL:
            MACHINE.reg[REG_A] *= getAddress(addr, nmode, true);
            break;

        case MULF:
            {
                flt f;
                f = loadFloat(addr, nmode);
                MACHINE.f.f *= f.f;
            }
            break;

        case OR:
            MACHINE.reg[REG_A] |= getAddress(addr, nmode, true);
            break;

        case RD:
            MACHINE.reg[REG_A] &= 0xFFFF00;
            MACHINE.reg[REG_A] |= readDevice(getAddress(addr, nmode, true));
            break;

        case RSUB:
            MACHINE.pc = MACHINE.reg[REG_L];
            break;

        case SSK:
            break;

        case STA:
            storeWord(REG_A, getAddress(addr, nmode, false));
            break;

        case STB:
            storeWord(REG_B, getAddress(addr, nmode, false));
            break;

        case STCH:
            storeByte(REG_A, getAddress(addr, nmode, false));
            break;

        case STF:
            storeFloat(addr);
            break;

        case STI:
            break;

        case STL:
            storeWord(REG_L, getAddress(addr, nmode, false));
            break;

        case STS:
            storeWord(REG_S, getAddress(addr, nmode, false));
            break;

        case STSW:
            break;

        case STT:
            storeWord(REG_T, getAddress(addr, nmode, false));
            break;

        case STX:
            storeWord(REG_X, getAddress(addr, nmode, false));
            break;

        case SUB:
            MACHINE.reg[REG_A] -= getAddress(addr, nmode, true);
            break;

        case SUBF:
            {
                flt f;
                f = loadFloat(addr, nmode);
                MACHINE.f.f -= f.f;
            }
            break;

        case TD:
            if (testDevice(getAddress(addr, nmode, true)) == true) {
                MACHINE.sw &= 0xFFFF3F;
            } else {
                MACHINE.sw &= 0xFFFF3F;
                MACHINE.sw |= 0x000040;
            }
            break;

        case TIX:
            {
                MACHINE.reg[REG_X]++;
                int32_t num = getAddress(addr, nmode, true);

                if (MACHINE.reg[REG_X] == num) {
                    MACHINE.sw &= 0xFFFF3F;
                    MACHINE.sw |= 0x000040;
                } else if (MACHINE.reg[REG_X] > num) {
                    MACHINE.sw &= 0xFFFF3F;
                    MACHINE.sw |= 0x000080;
                } else if (MACHINE.reg[REG_X] < num) {
                    MACHINE.sw &= 0xFFFF3F;
                }
            }
            break;

        case WD:
            writeDevice(getAddress(addr, nmode, true), MACHINE.reg[REG_A]);
            break;

        default:
            return false;
            break;
    }

    return true;
}

void execute() {
    MACHINE.opcode = MACHINE.mem[MACHINE.pc++];

    if (execF1() == true) {
        return;
    }

    uint8_t byte1 = MACHINE.mem[MACHINE.pc++];

    if (execF2(byte1) == true) {
        return;
    }

    exec(byte1);
}

void resetMachine() {
    running = false;
    reset = false;

    for (int i = 3; i < 256; i++) {
        if (MACHINE.devices[i] != NULL) {
            fclose(MACHINE.devices[i]);
        }
    }

    initMachine();

    if (programName != NULL) {
        load(programName);
    }
}

uint8_t length(uint32_t addr) {
    int pc = 0;
    OPCODE op = MACHINE.mem[addr] & 0xFC;

    switch (op) {
        case FIX: case FLOAT:
        case HIO: case NORM:
        case SIO: case TIO:
            pc = 1;
            break;

        case ADDR: case CLEAR:
        case COMPR: case DIVR:
        case MULR: case RMO:
        case SHIFTL: case SHIFTR:
        case SUBR: case SVC:
        case TIXR:
            pc = 2;
            break;

        default:
            if ((MACHINE.mem[addr + 1] & 0x10) > 0) {
                pc = 4;
            } else {
                pc = 3;
            }
            break;
    }

    return pc;
}

int main(int argc, char **argv) {
    programName = (char *)calloc(21, sizeof(char));
    initMachine();

    if (argc > 1) {
        programName = argv[1];
        load(programName);
    }

    int64_t lastPC = -1;

    initscr();

    start_color();
    init_pair(1, COLOR_GREEN, COLOR_BLACK);
    init_pair(2, COLOR_RED, COLOR_BLACK);
    init_pair(3, COLOR_YELLOW, COLOR_BLACK);

    int row, col;
    getmaxyx(stdscr, row, col);
    noecho();
    curs_set(false);

    WINDOW *registers = newwin(4, col, 0, 0);
    cpu = newwin(21, col, 4, 0);
    WINDOW *memory = newwin(34, col, row - 35, 0);
    WINDOW *screen = newwin(34, col, row - 35, 0);
    WINDOW *status = newwin(1, col, row - 1, 0);

    uint16_t speed = 31;
    uint8_t speedup = 1;

    keypad(cpu, true);
    wtimeout(cpu, speed);

    uint16_t mempos = 0;

    running = false;
    bool halt = false;
    bool memoryMode = true;
    reset = false;

    while (halt == false) {
        box(registers, ACS_VLINE, ACS_HLINE);
        wattron(registers, COLOR_PAIR(1));
        mvwprintw(registers, 0, 2, "Registers");
        wattroff(registers, COLOR_PAIR(1));

        mvwprintw(registers, 1, 2, "PC:  %05X", MACHINE.pc);
        mvwprintw(registers, 2, 2, "SW: %06X", MACHINE.sw);

        mvwprintw(registers, 1, 16, "A: %06X", MACHINE.reg[REG_A] & 0xFFFFFF);
        mvwprintw(registers, 1, 27, "X: %06X", MACHINE.reg[REG_X] & 0xFFFFFF);
        mvwprintw(registers, 1, 38, "L: %06X", MACHINE.reg[REG_L] & 0xFFFFFF);
        mvwprintw(registers, 2, 16, "B: %06X", MACHINE.reg[REG_B] & 0xFFFFFF);
        mvwprintw(registers, 2, 27, "S: %06X", MACHINE.reg[REG_S] & 0xFFFFFF);
        mvwprintw(registers, 2, 38, "T: %06X", MACHINE.reg[REG_T] & 0xFFFFFF);

        mvwprintw(registers, 1, 51, "F: %04X", (MACHINE.f.i >> 48));
        mvwprintw(registers, 1, 58, "%08X", (MACHINE.f.i >> 16));

        mvwprintw(registers, 2, 51, "-> %lf", MACHINE.f.f);

        wrefresh(registers);

        box(cpu, ACS_VLINE, ACS_HLINE);
        wattron(cpu, COLOR_PAIR(1));
        mvwprintw(cpu, 0, 2, "CPU");
        wattroff(cpu, COLOR_PAIR(1));

        mvwprintw(cpu, 1, 2, "Assembly:");
        mvwprintw(cpu, 2, 2, "Opcode:");
        mvwprintw(cpu, 3, 2, "Addressing:");
        mvwprintw(cpu, 4, 2, "Address:");
        mvwvline(cpu, 1, col / 2, ACS_VLINE, 19);

        if (running == true) {
            for (int i = 0; i < speedup; i++) {
                execute();

                if (MACHINE.pc == lastPC) {
                    running = false;
                    reset = true;
                    break;
                }

                lastPC = MACHINE.pc;
            }
        }

        uint32_t pc = MACHINE.pc;

        if (pc >= 15) {
            pc -= 15;
        } else {
            pc = 0;
        }

        for (int i = 0; i < 19; i++) {
            mvwprintw(cpu, i + 1, (col / 2) + 2 + 16, "      ");

            if (MACHINE.pc >= 3 && pc == MACHINE.pc - 3) {
                wattron(cpu, COLOR_PAIR(1));
            } else if (pc == MACHINE.pc) {
                wattron(cpu, COLOR_PAIR(3));
            }

            uint8_t next = length(pc);

            switch (next) {
                case 1:
                    mvwprintw(cpu, i + 1, (col / 2) + 2, "%05X  %02X        %6s", pc, MACHINE.mem[pc], OPCODES[(MACHINE.mem[pc] & 0xFC) >> 2]);
                    break;

                case 2:
                    mvwprintw(cpu, i + 1, (col / 2) + 2, "%05X  %02X%02X      %6s", pc, MACHINE.mem[pc], MACHINE.mem[pc + 1], OPCODES[(MACHINE.mem[pc] & 0xFC) >> 2]);
                    break;

                case 3:
                    mvwprintw(cpu, i + 1, (col / 2) + 2, "%05X  %02X%02X%02X    %6s", pc, MACHINE.mem[pc], MACHINE.mem[pc + 1], MACHINE.mem[pc + 2], OPCODES[(MACHINE.mem[pc] & 0xFC) >> 2]);
                    break;

                case 4:
                    mvwprintw(cpu, i + 1, (col / 2) + 2, "%05X  %02X%02X%02X%02X  %6s", pc, MACHINE.mem[pc], MACHINE.mem[pc + 1], MACHINE.mem[pc + 2], MACHINE.mem[pc + 3], OPCODES[(MACHINE.mem[pc] & 0xFC) >> 2]);
                    break;
            }

            if (MACHINE.pc >= 3 && pc == MACHINE.pc - 3) {
                wattroff(cpu, COLOR_PAIR(1));
            } else if (pc == MACHINE.pc) {
                wattroff(cpu, COLOR_PAIR(3));
            }

            pc += next;
        }

        wrefresh(cpu);

        if (memoryMode == true) {
            box(memory, ACS_VLINE, ACS_HLINE);
            mvwvline(memory, 1, 6, ACS_VLINE, 32);
            mvwvline(memory, 1, 54, ACS_VLINE, 32);
            wattron(memory, COLOR_PAIR(1));
            mvwprintw(memory, 0, 2, "Memory");
            wattroff(memory, COLOR_PAIR(1));
            mvwprintw(memory, 0, 10, "Screen");
            wattron(memory, COLOR_PAIR(2));
            if (mempos > 0) {
                mvwprintw(memory, 0, col - 3, "k");
            }
            if (mempos < 65536 - 32) {
                mvwprintw(memory, 33, col - 3, "j");
            }
            wattroff(memory, COLOR_PAIR(2));

            for (int i = 1; i < 33; i++) {
                mvwprintw(memory, i, 1, "%05X", (mempos + i - 1) * 16);

                for (int j = 7, k = 0; k < 16; j += 3, k++) {
                    uint8_t data = MACHINE.mem[((mempos + i - 1) * 16) + k];
                    char c = '.';

                    if (data >= 32 && data <= 126) {
                        c = data;
                    }

                    mvwprintw(memory, i, j, "%02X", data);
                    mvwprintw(memory, i, 55 + k, "%c", c);
                }
            }

            wrefresh(memory);
        } else {
            box(screen, ACS_VLINE, ACS_HLINE);
            mvwprintw(screen, 0, 2, "Memory");
            wattron(screen, COLOR_PAIR(1));
            mvwprintw(screen, 0, 10, "Screen");
            wattroff(screen, COLOR_PAIR(1));

            uint32_t scraddr = 0xB8000;

            for (int i = 0; i < 32; i++) {
                for (int j = 0; j < 70; j++) {
                    mvwprintw(screen, i + 1, j + 1, " ");
                    mvwprintw(screen, i + 1, j + 1, "%c", MACHINE.mem[scraddr + (i * 70) + j]);
                }
            }

            wrefresh(screen);
        }

        mvwprintw(status, 0, 1, "[ ");

        if (running == true) {
            wattron(status, COLOR_PAIR(2));
            wprintw(status, "Stop ");
            wattroff(status, COLOR_PAIR(2));
        } else {
            wattron(status, COLOR_PAIR(1));
            wprintw(status, "Start");
            wattroff(status, COLOR_PAIR(1));
        }

        wprintw(status, " | Step | ");

        if (reset == true) {
            wattron(status, COLOR_PAIR(3));
            wprintw(status, "Reset");
            wattroff(status, COLOR_PAIR(3));
        } else {
            wprintw(status, "Reset");
        }

        wprintw(status, " ]  [ Open | Goto ]  [ Clock: %4d Hz | x%2d ]", 1000 / speed, speedup);

        wrefresh(status);

        int key = wgetch(cpu);

        switch (key) {
            case 'g': case 'G':
                {
                    char memory[6];
                    echo();
                    wtimeout(cpu, -1);
                    wattron(status, COLOR_PAIR(3));
                    mvwprintw(status, 0, 36, "Goto");
                    wattroff(status, COLOR_PAIR(3));
                    mvwprintw(status, 0, 44, "[       ]               ");
                    wrefresh(status);
                    mvwgetnstr(status, 0, 46, memory, 5);
                    wtimeout(cpu, speed);
                    noecho();

                    mvwprintw(cpu, 10, 10, "     ");
                    mempos = strtoul(memory, NULL, 16) / 16;

                    if (mempos > 65536 - 32) {
                        mempos = 65536 - 32;
                    }
                }
                break;

            case 'j': case 'J':
                if (mempos < 65536 - 32) {
                    mempos++;
                }
                break;

            case 'k': case 'K':
                if (mempos > 0) {
                    mempos--;
                }
                break;

            case 'n': case 'N':
                execute();
                break;

            case 'o': case 'O':
                echo();
                wtimeout(cpu, -1);
                wattron(status, COLOR_PAIR(3));
                mvwprintw(status, 0, 29, "Open");
                wattroff(status, COLOR_PAIR(3));
                mvwprintw(status, 0, 44, "[                      ]");
                wrefresh(status);
                mvwgetnstr(status, 0, 46, programName, 20);
                wtimeout(cpu, speed);
                noecho();

                resetMachine();
                break;

            case 'r': case 'R':
                resetMachine();
                break;

            case 's': case 'S':
                running = !running;
                break;

            case 'q': case 'Q':
                halt = true;
                break;

            case '\t':
                memoryMode = !memoryMode;
                break;

            case '-': case '\'':
                if (speedup > 1) {
                    speedup--;
                }
                break;

            case '=': case '+':
                if (speedup < 16) {
                    speedup++;
                }
                break;

            case '9':
                if (speed < 62) {
                    wtimeout(cpu, ++speed);
                }
                break;

            case '0':
                if (speed > 1) {
                    wtimeout(cpu, --speed);
                }
                break;
        }
    }

    endwin();

    return 0;
}
