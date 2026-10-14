// Opt-in adb-shell comparison using the same static whisper.cpp libraries as the app.
// Does not open a microphone, access app-private files, execute commands or change settings.
#include "whisper.h"
#include <chrono>
#include <cstdint>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>
#include <algorithm>

using Clock = std::chrono::steady_clock;
static bool timed_out(void *opaque) { return Clock::now() > *static_cast<Clock::time_point *>(opaque); }
static std::string json(const std::string &s) {
    std::string r = "\"";
    for (unsigned char c : s) {
        if (c == '"' || c == '\\') { r += '\\'; r += c; }
        else if (c == '\n') r += "\\n";
        else if (c == '\r') r += "\\r";
        else if (c == '\t') r += "\\t";
        else if (c >= 32) r += c;
    }
    return r + '"';
}
static std::vector<float> read_wav(const std::string &path) {
    std::ifstream f(path, std::ios::binary);
    char header[44]; f.read(header,44);
    if (!f || std::string(header,4)!="RIFF" || std::string(header+36,4)!="data") throw std::runtime_error("Expected canonical PCM16 WAV");
    const auto u16=[&](int p){return uint16_t(uint8_t(header[p]) | uint16_t(uint8_t(header[p+1]))<<8);};
    const auto u32=[&](int p){return uint32_t(u16(p)) | uint32_t(u16(p+2))<<16;};
    if(u16(20)!=1 || u16(22)!=1 || u32(24)!=16000 || u16(34)!=16)throw std::runtime_error("Expected mono 16k PCM16");
    std::vector<float> samples; char b[2];
    while(f.read(b,2)) samples.push_back(static_cast<int16_t>(uint16_t(uint8_t(b[0])) | uint16_t(uint8_t(b[1]))<<8)/32768.f);
    if(samples.empty() || samples.size()>30*16000)throw std::runtime_error("Expected 0–30 seconds");
    return samples;
}
int main(int argc,char **argv) try {
    if(argc!=4 && argc!=5) throw std::runtime_error("model manifest.tsv result.json [first-extra-variant]");
    const int first_extra=argc==5?std::stoi(argv[4]):1;
    auto cp=whisper_context_default_params();cp.use_gpu=false;cp.flash_attn=true;
    auto *ctx=whisper_init_from_file_with_params_no_state(argv[1],cp);
    if(!ctx)throw std::runtime_error("Model load failed");
    std::ifstream manifest(argv[2]);if(!manifest)throw std::runtime_error("Manifest missing");
    std::ofstream out(argv[3]);out<<"[\n"; bool first=true;std::string line;
    const std::string short_prompt="Напомни, напоминание, заметка, создай, удали, покажи, отмени, сегодня, завтра, послезавтра, утром, днём, вечером, через, минут, часов, дней.";
    const std::string full_prompt="Напомни, напоминание, заметка, создай, удали, покажи, отмени, сегодня, завтра, послезавтра, утром, днём, вечером, через, минут, часов, дней, добавь, запиши, сохрани, выполни, перенеси, каждый, ежедневно, еженедельно, ежемесячно, папка.";
    bool warmed=false;
    while(std::getline(manifest,line)) {
        std::stringstream ss(line);std::string id,path,expected;std::getline(ss,id,'\t');std::getline(ss,path,'\t');std::getline(ss,expected);
        auto audio=read_wav(path);
        const int required=(audio.size()+319)/320+(audio.size()<=67200?4:24);
        const int base_ctx=required<=208?208:required<=1280?((required+31)/32)*32:0;
        const std::string base_prompt=base_ctx>0&&base_ctx<512?short_prompt:full_prompt;
        for(int variant=warmed?0:-1;variant<12;variant++) {
            if(variant>0 && variant<first_extra)continue;
            int window=variant==2?std::max(512,base_ctx):base_ctx;
            if(base_ctx==0)window=0;
            int beam=variant==5?3:(variant==3||variant==6||variant==7||variant>=10)?2:1;
            std::string prompt=(variant==4||variant==6)?"":(variant==1||variant==7)?base_prompt+" в.":base_prompt;
            if(variant==8||variant==10)prompt="Напомни, покажи, создай, удали, добавь, запиши, сохрани, отмени.";
            if(variant==9||variant==11)prompt="Напомни. Покажи. Создай. Удали. Добавь. Запиши. Сохрани. Отмени.";
            auto p=whisper_full_default_params(beam>1?WHISPER_SAMPLING_BEAM_SEARCH:WHISPER_SAMPLING_GREEDY);
            p.n_threads=6;p.language="ru";p.initial_prompt=prompt.empty()?nullptr:prompt.c_str();
            p.translate=false;p.no_context=true;p.carry_initial_prompt=false;p.no_timestamps=true;
            p.single_segment=audio.size()<=12*16000;p.print_progress=false;p.print_realtime=false;p.print_timestamps=false;p.print_special=false;
            p.suppress_blank=true;p.suppress_nst=true;p.vad=false;p.audio_ctx=window;p.max_tokens=0;p.n_max_text_ctx=256;
            p.greedy.best_of=window>0&&window<=256?2:1;p.beam_search.beam_size=beam;p.temperature_inc=0;
            auto deadline=Clock::now()+std::chrono::seconds(20);p.abort_callback=timed_out;p.abort_callback_user_data=&deadline;
            auto *state=whisper_init_state(ctx);if(!state)throw std::runtime_error("State failed");
            const auto start=Clock::now();const int status=whisper_full_with_state(ctx,state,p,audio.data(),audio.size());
            const auto ms=std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now()-start).count();
            std::string text;for(int n=0;n<whisper_full_n_segments_from_state(state);n++)text+=whisper_full_get_segment_text_from_state(state,n);
            whisper_free_state(state);
            if(variant<0){warmed=true;continue;}
            if(!first)out<<",\n";first=false;
            out<<"{\"id\":"<<json(id)<<",\"expected\":"<<json(expected)<<",\"variant\":"<<variant<<",\"context\":"<<window<<",\"beam\":"<<beam<<",\"ms\":"<<ms<<",\"status\":"<<status<<",\"text\":"<<json(text)<<"}";out.flush();
            std::cout<<id<<" variant="<<variant<<" ms="<<ms<<" status="<<status<<std::endl;
        }
    }
    out<<"\n]\n";whisper_free(ctx);return 0;
}catch(const std::exception &e){std::cerr<<e.what()<<std::endl;return 1;}
